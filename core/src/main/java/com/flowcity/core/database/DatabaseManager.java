package com.flowcity.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connection pool + dedicated database thread pool.
 *
 * Rule for the whole project: gameplay code never touches JDBC on the server main thread.
 * It calls {@link #query} / {@link #transaction}, which run on the FlowCity-DB threads and
 * return a CompletableFuture; results are handed back to the main thread by the caller
 * (see {@code com.flowcity.core.util.Tasks}).
 *
 * Bukkit-independent on purpose so it can be unit-tested with plain JDBC.
 */
public final class DatabaseManager {

    private final Logger log;
    private final DatabaseSettings settings;
    private final ClassLoader resources;

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private volatile int schemaVersion;

    public DatabaseManager(Logger log, DatabaseSettings settings, ClassLoader resources) {
        this.log = log;
        this.settings = settings;
        this.resources = resources;
    }

    /** Opens the pool and runs migrations. Called once at plugin enable (startup, not gameplay). */
    public void start() throws SQLException, IOException {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("FlowCity-Pool");
        cfg.setConnectionTimeout(settings.connectionTimeoutMs);
        cfg.setMaxLifetime(settings.maxLifetimeMs);

        int threads;
        if (settings.dialect == Dialect.SQLITE) {
            File file = settings.sqliteFile.getAbsoluteFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Cannot create folder " + parent);
            }
            cfg.setDriverClassName("org.sqlite.JDBC");
            cfg.setJdbcUrl("jdbc:sqlite:" + file.getPath());
            // SQLite supports a single writer: one pooled connection, one DB thread => no SQLITE_BUSY races.
            cfg.setMaximumPoolSize(1);
            cfg.setMinimumIdle(1);
            cfg.addDataSourceProperty("foreign_keys", "true");
            cfg.addDataSourceProperty("busy_timeout", "5000");
            threads = 1;
        } else {
            cfg.setDriverClassName(com.mysql.cj.jdbc.Driver.class.getName());
            cfg.setJdbcUrl(mysqlUrl());
            cfg.setUsername(settings.username);
            cfg.setPassword(settings.password);
            cfg.setMaximumPoolSize(settings.maximumPoolSize);
            cfg.setMinimumIdle(settings.minimumIdle);
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            cfg.addDataSourceProperty("useServerPrepStmts", "true");
            cfg.addDataSourceProperty("rewriteBatchedStatements", "true");
            threads = settings.maximumPoolSize;
        }

        dataSource = new HikariDataSource(cfg);
        executor = Executors.newFixedThreadPool(threads, new NamedThreadFactory("FlowCity-DB-"));

        try (Connection c = dataSource.getConnection()) {
            if (settings.dialect == Dialect.SQLITE) {
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                }
            }
            MigrationRunner runner = new MigrationRunner(log, resources);
            List<MigrationRunner.Migration> pending = runner.pending(c);
            int appliedCount = runner.appliedVersions(c).size();
            // Back up only an existing schema; a brand-new database has nothing to lose.
            if (!pending.isEmpty() && appliedCount > 0
                    && settings.dialect == Dialect.SQLITE && settings.backupBeforeMigrations) {
                backupSqlite(c, appliedCount);
            }
            schemaVersion = runner.migrate(c, settings.dialect);
        } catch (SQLException | IOException | RuntimeException e) {
            shutdown();
            throw e;
        }
        log.info("Database ready: " + settings.dialect.id() + ", schema version " + schemaVersion);
    }

    private String mysqlUrl() {
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(settings.host).append(':').append(settings.port).append('/').append(settings.database)
                .append("?useSSL=").append(settings.useSsl);
        for (Map.Entry<String, String> e : settings.properties.entrySet()) {
            url.append('&').append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return url.toString();
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Consistent online copy via VACUUM INTO (SQLite 3.27+), then prunes old backups. */
    private void backupSqlite(Connection c, int currentVersion) throws SQLException, IOException {
        File folder = settings.backupFolder != null ? settings.backupFolder
                : new File(settings.sqliteFile.getAbsoluteFile().getParentFile(), "backups");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create backup folder " + folder);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(folder, "flowcity-" + stamp + "-v" + currentVersion + ".db");
        try (Statement st = c.createStatement()) {
            st.execute("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'");
        }
        log.info("SQLite backup written to " + target.getName());

        File[] backups = folder.listFiles((dir, name) -> name.startsWith("flowcity-") && name.endsWith(".db"));
        if (backups != null && backups.length > settings.backupKeep) {
            Arrays.sort(backups, Comparator.comparing(File::getName));
            for (int i = 0; i < backups.length - settings.backupKeep; i++) {
                if (!backups[i].delete()) {
                    log.warning("Could not delete old backup " + backups[i].getName());
                }
            }
        }
    }

    /** Runs work with auto-commit on a database thread. */
    public <T> CompletableFuture<T> query(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                return work.apply(c);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Runs work inside one transaction; commits on success, rolls back on any exception. */
    public <T> CompletableFuture<T> transaction(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                boolean old = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    T result = work.apply(c);
                    c.commit();
                    return result;
                } catch (SQLException | RuntimeException e) {
                    try {
                        c.rollback();
                    } catch (SQLException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                    throw e;
                } finally {
                    c.setAutoCommit(old);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public Dialect dialect() {
        return settings.dialect;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** "active/idle/total (waiting)" or "closed". */
    public String poolStats() {
        if (dataSource == null || dataSource.isClosed()) {
            return "closed";
        }
        HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
        if (bean == null) {
            return "starting";
        }
        return bean.getActiveConnections() + " active / " + bean.getIdleConnections() + " idle / "
                + bean.getTotalConnections() + " total (" + bean.getThreadsAwaitingConnection() + " waiting)";
    }

    /** Lets queued work finish (max 10 s), then closes the pool. Safe to call more than once. */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warning("Database tasks still running after 10s; forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Error closing database pool", e);
            }
            dataSource = null;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger n = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + n.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
