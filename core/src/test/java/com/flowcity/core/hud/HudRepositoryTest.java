package com.flowcity.core.hud;

import com.flowcity.common.hud.HudLayout;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HudRepositoryTest {

    @TempDir
    Path dir;

    private DatabaseManager db;
    private final HudRepository repo = new HudRepository();
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");

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

    @Test
    void newPlayerHasNoPresets() {
        HudRepository.Loaded l = db.query(c -> repo.load(c, a)).join();
        assertEquals(0, l.activeSlot);
        assertTrue(l.presets.isEmpty());
    }

    @Test
    void saveActivatesAndOverwrites() {
        String layout = HudLayout.defaults().encode();
        db.transaction(c -> {
            repo.saveAndActivate(c, a, 2, "Mine", layout, 1L);
            return null;
        }).join();
        HudRepository.Loaded l = db.query(c -> repo.load(c, a)).join();
        assertEquals(2, l.activeSlot);
        assertEquals(1, l.presets.size());
        assertEquals("Mine", l.presets.get(0).name);
        assertEquals(layout, l.presets.get(0).layout);

        HudLayout changed = HudLayout.defaults();
        changed.uiScalePercent = 120;
        db.transaction(c -> {
            repo.saveAndActivate(c, a, 2, "Renamed", changed.encode(), 2L);
            repo.saveAndActivate(c, a, 1, "First", layout, 3L);
            return null;
        }).join();
        l = db.query(c -> repo.load(c, a)).join();
        assertEquals(1, l.activeSlot);
        assertEquals(2, l.presets.size());
        assertEquals("First", l.presets.get(0).name);
        assertEquals("Renamed", l.presets.get(1).name);
        assertEquals(120, HudLayout.parse(l.presets.get(1).layout).uiScalePercent);
    }

    @Test
    void playersDoNotSeeEachOther() {
        db.transaction(c -> {
            repo.saveAndActivate(c, a, 1, "A", HudLayout.defaults().encode(), 1L);
            return null;
        }).join();
        assertTrue(db.query(c -> repo.load(c, b)).join().presets.isEmpty());
    }

    @Test
    void presetNamesAreCleaned() {
        assertEquals("Preset 3", HudConfigService.cleanName("", 3));
        assertEquals("Preset 1", HudConfigService.cleanName("\u00a7\u00a7<>", 1));
        assertEquals("My HUD 2", HudConfigService.cleanName(" My HUD 2 ", 1));
        assertEquals(16, HudConfigService.cleanName("abcdefghijklmnopqrstuvwxyz", 1).length());
    }
}
