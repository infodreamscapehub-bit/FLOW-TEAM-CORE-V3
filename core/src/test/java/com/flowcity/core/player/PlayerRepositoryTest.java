package com.flowcity.core.player;

import com.flowcity.core.database.DatabaseManager;
import com.flowcity.core.database.DatabaseSettings;
import com.flowcity.core.database.Dialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRepositoryTest {

    @TempDir
    Path dir;

    private DatabaseManager db;
    private final PlayerRepository repo = new PlayerRepository(Dialect.SQLITE);
    private final IdRules rules = new IdRules(1, 99999, false);

    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private final UUID admin = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    @BeforeEach
    void open() throws Exception {
        File file = dir.resolve("flowcity.db").toFile();
        db = new DatabaseManager(Logger.getLogger("test"),
                DatabaseSettings.builder(Dialect.SQLITE).sqliteFile(file).build(), getClass().getClassLoader());
        db.start();
    }

    @AfterEach
    void close() {
        db.shutdown();
    }

    private PlayerRecord join(UUID uuid, String name) {
        return db.transaction(conn -> repo.loadOrCreate(conn, uuid, name, System.currentTimeMillis(), rules)).join();
    }

    private IdChangeException.Reason failureOf(Runnable r) {
        CompletionException ex = assertThrows(CompletionException.class, r::run);
        assertTrue(ex.getCause() instanceof IdChangeException, "cause: " + ex.getCause());
        return ((IdChangeException) ex.getCause()).reason();
    }

    @Test
    void firstJoinGetsSequentialIdsAndReconnectKeepsThem() {
        assertEquals(1, join(a, "Alpha").rpId);
        assertEquals(2, join(b, "Bravo").rpId);
        PlayerRecord again = join(a, "AlphaRenamed");
        assertEquals(1, again.rpId, "ID must survive reconnect");
        assertEquals("AlphaRenamed", again.lastName);
    }

    @Test
    void setIdRejectsTakenAndSameAndOutOfRange() {
        join(a, "Alpha");
        join(b, "Bravo");
        assertEquals(IdChangeException.Reason.TAKEN, failureOf(() ->
                db.transaction(conn -> repo.changeId(conn, b, 1, admin, "t", 0, rules)).join()));
        assertEquals(IdChangeException.Reason.SAME, failureOf(() ->
                db.transaction(conn -> repo.changeId(conn, b, 2, admin, "t", 0, rules)).join()));
        assertEquals(IdChangeException.Reason.OUT_OF_RANGE, failureOf(() ->
                db.transaction(conn -> repo.changeId(conn, b, 100000, admin, "t", 0, rules)).join()));
    }

    @Test
    void oldIdsAreNeverReused() {
        join(a, "Alpha");                       // 1
        join(b, "Bravo");                       // 2
        int old = db.transaction(conn -> repo.changeId(conn, b, 7, admin, "vip", 0, rules)).join();
        assertEquals(2, old);

        // 2 is retired: a new player gets 3, and nobody may take 2 back.
        assertEquals(3, join(c, "Charlie").rpId);
        assertEquals(IdChangeException.Reason.RETIRED, failureOf(() ->
                db.transaction(conn -> repo.changeId(conn, c, 2, admin, "t", 0, rules)).join()));

        // reset gives the next counter value and retires the old one
        int[] reset = db.transaction(conn -> repo.resetId(conn, a, admin, "reset", 0, rules)).join();
        assertEquals(1, reset[0]);
        assertEquals(4, reset[1]);

        // allocation skips 7 (assigned by setid) when the counter reaches it
        for (int i = 0; i < 2; i++) {
            join(UUID.randomUUID(), "p" + i);   // 5, 6
        }
        assertEquals(8, join(UUID.randomUUID(), "p8").rpId);
    }

    @Test
    void failedChangeRollsBack() {
        join(a, "Alpha");
        join(b, "Bravo");
        failureOf(() -> db.transaction(conn -> repo.changeId(conn, b, 1, admin, "t", 0, rules)).join());
        PlayerRecord still = db.query(conn -> repo.findByUuid(conn, b)).join();
        assertEquals(2, still.rpId);
    }

    @Test
    void reuseCanBeEnabled() {
        IdRules reuse = new IdRules(1, 99999, true);
        db.transaction(conn -> repo.loadOrCreate(conn, a, "Alpha", 0, reuse)).join();
        db.transaction(conn -> repo.changeId(conn, a, 50, admin, "t", 0, reuse)).join();
        PlayerRecord p = db.transaction(conn -> repo.loadOrCreate(conn, b, "Bravo", 0, reuse)).join();
        assertEquals(2, p.rpId);
        // with reuse on, the freed ID 1 may be assigned manually
        db.transaction(conn -> repo.changeId(conn, b, 1, admin, "t", 0, reuse)).join();
        assertEquals(1, db.query(conn -> repo.findByUuid(conn, b)).join().rpId);
    }

    @Test
    void lookupByIdAndName() {
        join(a, "Alpha");
        assertEquals(a, db.query(conn -> repo.findByRpId(conn, 1)).join().uuid);
        assertEquals(a, db.query(conn -> repo.findByName(conn, "alpha")).join().uuid);
        assertNull(db.query(conn -> repo.findByRpId(conn, 999)).join());
    }

    @Test
    void sessionSaveStoresPlaytime() {
        join(a, "Alpha");
        db.transaction(conn -> {
            repo.saveSession(conn, a, "Alpha", 123L, 3600L);
            return null;
        }).join();
        PlayerRecord p = db.query(conn -> repo.findByUuid(conn, a)).join();
        assertEquals(3600L, p.playtimeSeconds);
        assertEquals(123L, p.lastSeen);
    }
}
