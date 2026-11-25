package com.flowcity.core.database;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable database settings. Built from database.yml by {@code DatabaseSettingsLoader}. */
public final class DatabaseSettings {

    public final Dialect dialect;

    // SQLite
    public final File sqliteFile;

    // MySQL
    public final String host;
    public final int port;
    public final String database;
    public final String username;
    public final String password;
    public final boolean useSsl;
    public final Map<String, String> properties;

    // Pool
    public final int maximumPoolSize;
    public final int minimumIdle;
    public final long connectionTimeoutMs;
    public final long maxLifetimeMs;

    // Backups
    public final boolean backupBeforeMigrations;
    public final int backupKeep;
    public final File backupFolder;

    private DatabaseSettings(Builder b) {
        this.dialect = b.dialect;
        this.sqliteFile = b.sqliteFile;
        this.host = b.host;
        this.port = b.port;
        this.database = b.database;
        this.username = b.username;
        this.password = b.password;
        this.useSsl = b.useSsl;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(b.properties));
        this.maximumPoolSize = b.maximumPoolSize;
        this.minimumIdle = b.minimumIdle;
        this.connectionTimeoutMs = b.connectionTimeoutMs;
        this.maxLifetimeMs = b.maxLifetimeMs;
        this.backupBeforeMigrations = b.backupBeforeMigrations;
        this.backupKeep = b.backupKeep;
        this.backupFolder = b.backupFolder;
    }

    public static Builder builder(Dialect dialect) {
        return new Builder(dialect);
    }

    public static final class Builder {
        private final Dialect dialect;
        private File sqliteFile;
        private String host = "localhost";
        private int port = 3306;
        private String database = "flowcity";
        private String username = "flowcity";
        private String password = "";
        private boolean useSsl;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private int maximumPoolSize = 8;
        private int minimumIdle = 2;
        private long connectionTimeoutMs = 10_000;
        private long maxLifetimeMs = 1_800_000;
        private boolean backupBeforeMigrations = true;
        private int backupKeep = 10;
        private File backupFolder;

        private Builder(Dialect dialect) {
            this.dialect = dialect;
        }

        public Builder sqliteFile(File f) { this.sqliteFile = f; return this; }
        public Builder host(String v) { this.host = v; return this; }
        public Builder port(int v) { this.port = v; return this; }
        public Builder database(String v) { this.database = v; return this; }
        public Builder username(String v) { this.username = v; return this; }
        public Builder password(String v) { this.password = v; return this; }
        public Builder useSsl(boolean v) { this.useSsl = v; return this; }
        public Builder property(String k, String v) { this.properties.put(k, v); return this; }
        public Builder maximumPoolSize(int v) { this.maximumPoolSize = v; return this; }
        public Builder minimumIdle(int v) { this.minimumIdle = v; return this; }
        public Builder connectionTimeoutMs(long v) { this.connectionTimeoutMs = v; return this; }
        public Builder maxLifetimeMs(long v) { this.maxLifetimeMs = v; return this; }
        public Builder backupBeforeMigrations(boolean v) { this.backupBeforeMigrations = v; return this; }
        public Builder backupKeep(int v) { this.backupKeep = v; return this; }
        public Builder backupFolder(File v) { this.backupFolder = v; return this; }

        public DatabaseSettings build() {
            if (dialect == Dialect.SQLITE && sqliteFile == null) {
                throw new IllegalArgumentException("sqlite.file is required");
            }
            if (maximumPoolSize < 1) {
                throw new IllegalArgumentException("pool.maximum-pool-size must be >= 1");
            }
            if (minimumIdle < 0 || minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("pool.minimum-idle must be between 0 and maximum-pool-size");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("mysql.port out of range");
            }
            return new DatabaseSettings(this);
        }
    }
}
