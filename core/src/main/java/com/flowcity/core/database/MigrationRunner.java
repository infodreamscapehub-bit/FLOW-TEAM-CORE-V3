package com.flowcity.core.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Versioned schema migrations.
 *
 * resources/db/migrations.txt lists every migration as {@code <version>:<name>}, in order.
 * Each one has a file per dialect: resources/db/&lt;dialect&gt;/V&lt;version&gt;__&lt;name&gt;.sql.
 * Applied versions are stored in fc_schema_version. Statements are separated by ';' at the end
 * of a line; lines starting with "--" are comments.
 *
 * Note: on MySQL, DDL statements commit implicitly, so a failed migration can leave partial
 * DDL behind. Every DDL statement therefore uses IF NOT EXISTS so a fixed migration can re-run.
 */
public final class MigrationRunner {

    public static final String INDEX = "db/migrations.txt";

    private final Logger log;
    private final ClassLoader resources;

    public MigrationRunner(Logger log, ClassLoader resources) {
        this.log = log;
        this.resources = resources;
    }

    public static final class Migration {
        public final int version;
        public final String name;

        Migration(int version, String name) {
            this.version = version;
            this.name = name;
        }

        String file(Dialect dialect) {
            return "db/" + dialect.id() + "/V" + version + "__" + name + ".sql";
        }
    }

    public List<Migration> knownMigrations() throws IOException {
        List<Migration> list = new ArrayList<>();
        int last = 0;
        for (String line : readLines(INDEX)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int colon = t.indexOf(':');
            if (colon <= 0) {
                throw new IOException("Bad line in " + INDEX + ": " + t);
            }
            int version = Integer.parseInt(t.substring(0, colon).trim());
            if (version != last + 1) {
                throw new IOException("Migrations must be numbered 1,2,3... without gaps; got " + version + " after " + last);
            }
            last = version;
            list.add(new Migration(version, t.substring(colon + 1).trim()));
        }
        return list;
    }

    public void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS fc_schema_version ("
                    + "version INT NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "applied_at BIGINT NOT NULL)");
        }
    }

    public Set<Integer> appliedVersions(Connection c) throws SQLException {
        Set<Integer> applied = new TreeSet<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version FROM fc_schema_version")) {
            while (rs.next()) {
                applied.add(rs.getInt(1));
            }
        }
        return applied;
    }

    /** Migrations not yet applied, in order. Fails if the database is newer than this plugin. */
    public List<Migration> pending(Connection c) throws SQLException, IOException {
        ensureVersionTable(c);
        List<Migration> known = knownMigrations();
        Set<Integer> applied = appliedVersions(c);
        int newestKnown = known.isEmpty() ? 0 : known.get(known.size() - 1).version;
        for (int v : applied) {
            if (v > newestKnown) {
                throw new SQLException("Database schema version " + v + " is newer than this FlowCityCore build ("
                        + newestKnown + "). Refusing to start to avoid data loss; update the plugin.");
            }
        }
        List<Migration> pending = new ArrayList<>();
        for (Migration m : known) {
            if (!applied.contains(m.version)) {
                pending.add(m);
            }
        }
        return pending;
    }

    /** Applies all pending migrations, each in its own transaction. Returns the resulting schema version. */
    public int migrate(Connection c, Dialect dialect) throws SQLException, IOException {
        List<Migration> pending = pending(c);
        for (Migration m : pending) {
            List<String> statements = splitStatements(readLines(m.file(dialect)));
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    for (String sql : statements) {
                        st.execute(sql);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_schema_version (version, name, applied_at) VALUES (?, ?, ?)")) {
                    ps.setInt(1, m.version);
                    ps.setString(2, m.name);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                c.commit();
                log.info("Applied database migration V" + m.version + " (" + m.name + ")");
            } catch (SQLException e) {
                c.rollback();
                throw new SQLException("Migration V" + m.version + " (" + m.name + ") failed: " + e.getMessage(), e);
            } finally {
                c.setAutoCommit(oldAutoCommit);
            }
        }
        Set<Integer> applied = appliedVersions(c);
        return applied.isEmpty() ? 0 : ((TreeSet<Integer>) applied).last();
    }

    static List<String> splitStatements(List<String> lines) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            cur.append(line).append('\n');
            if (t.endsWith(";")) {
                String sql = cur.toString().trim();
                out.add(sql.substring(0, sql.length() - 1).trim());
                cur.setLength(0);
            }
        }
        if (cur.toString().trim().length() > 0) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    private List<String> readLines(String path) throws IOException {
        InputStream in = resources.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Missing resource " + path);
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
