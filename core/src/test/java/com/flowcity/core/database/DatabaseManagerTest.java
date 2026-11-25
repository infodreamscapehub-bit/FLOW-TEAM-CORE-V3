package com.flowcity.core.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    private static final Logger LOG = Logger.getLogger("test");

    private DatabaseManager open(File dbFile) throws Exception {
        DatabaseSettings s = DatabaseSettings.builder(Dialect.SQLITE)
                .sqliteFile(dbFile)
                .backupFolder(new File(dbFile.getParentFile(), "backups"))
                .build();
        DatabaseManager db = new DatabaseManager(LOG, s, getClass().getClassLoader());
        db.start();
        return db;
    }

    @Test
    void freshDatabaseIsMigratedToLatest(@TempDir Path dir) throws Exception {
        DatabaseManager db = open(dir.resolve("flowcity.db").toFile());
        try {
            assertEquals(3, db.schemaVersion());
            long next = db.query(c -> {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT next_value FROM fc_counters WHERE name='rp_id'")) {
                    assertTrue(rs.next());
                    return rs.getLong(1);
                }
            }).join();
            assertEquals(1L, next);
        } finally {
            db.shutdown();
        }
        assertFalse(new File(dir.toFile(), "backups").exists(), "no backup for a brand-new database");
    }

    @Test
    void restartIsIdempotentAndKeepsData(@TempDir Path dir) throws Exception {
        File file = dir.resolve("flowcity.db").toFile();
        DatabaseManager db = open(file);
        db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO fc_players (uuid, rp_id, last_name, first_join, last_seen) VALUES (?,?,?,?,?)")) {
                ps.setString(1, "00000000-0000-0000-0000-000000000007");
                ps.setInt(2, 7);
                ps.setString(3, "Fabian");
                ps.setLong(4, 1L);
                ps.setLong(5, 1L);
                return ps.executeUpdate();
            }
        }).join();
        db.shutdown();

        DatabaseManager again = open(file);
        try {
            assertEquals(3, again.schemaVersion());
            String name = again.query(c -> {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT last_name FROM fc_players WHERE rp_id = 7")) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }).join();
            assertEquals("Fabian", name);
        } finally {
            again.shutdown();
        }
    }

    @Test
    void failedTransactionRollsBack(@TempDir Path dir) throws Exception {
        DatabaseManager db = open(dir.resolve("flowcity.db").toFile());
        try {
            CompletionException ex = assertThrows(CompletionException.class, () -> db.transaction(c -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("UPDATE fc_counters SET next_value = 500 WHERE name = 'rp_id'");
                    // Duplicate primary key -> SQLException -> whole transaction must roll back.
                    st.executeUpdate("INSERT INTO fc_counters (name, next_value) VALUES ('rp_id', 1)");
                }
                return null;
            }).join());
            assertTrue(ex.getCause() instanceof SQLException);

            long next = db.query(c -> {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT next_value FROM fc_counters WHERE name='rp_id'")) {
                    rs.next();
                    return rs.getLong(1);
                }
            }).join();
            assertEquals(1L, next, "update before the failure must be rolled back");
        } finally {
            db.shutdown();
        }
    }

    @Test
    void refusesDatabaseNewerThanPlugin(@TempDir Path dir) throws Exception {
        File file = dir.resolve("flowcity.db").toFile();
        DatabaseManager db = open(file);
        db.query(c -> {
            try (Statement st = c.createStatement()) {
                return st.executeUpdate("INSERT INTO fc_schema_version (version, name, applied_at) VALUES (99, 'future', 0)");
            }
        }).join();
        db.shutdown();

        assertThrows(SQLException.class, () -> open(file));
    }

    @Test
    void splitsStatementsAndSkipsComments() {
        List<String> sql = MigrationRunner.splitStatements(Arrays.asList(
                "-- comment",
                "CREATE TABLE a (",
                "  x INT",
                ");",
                "",
                "INSERT INTO a VALUES (1);"));
        assertEquals(2, sql.size());
        assertTrue(sql.get(0).startsWith("CREATE TABLE a"));
        assertEquals("INSERT INTO a VALUES (1)", sql.get(1));
    }
}
