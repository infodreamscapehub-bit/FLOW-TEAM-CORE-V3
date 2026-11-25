package com.flowcity.core.hud;

import com.flowcity.common.protocol.packets.HudConfigPacket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQL for per-player HUD presets. Callers run it through DatabaseManager (never on the main thread). */
public final class HudRepository {

    public static final class Loaded {
        public final int activeSlot;
        public final List<HudConfigPacket.Preset> presets;

        Loaded(int activeSlot, List<HudConfigPacket.Preset> presets) {
            this.activeSlot = activeSlot;
            this.presets = presets;
        }
    }

    public Loaded load(Connection c, UUID uuid) throws SQLException {
        List<HudConfigPacket.Preset> presets = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT slot, name, layout FROM fc_hud_presets WHERE uuid = ? ORDER BY slot")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    presets.add(new HudConfigPacket.Preset(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        int active = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT slot FROM fc_hud_active WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    active = rs.getInt(1);
                }
            }
        }
        return new Loaded(active, presets);
    }

    /** Writes the preset and makes it the active one. Call inside a transaction. */
    public void saveAndActivate(Connection c, UUID uuid, int slot, String name, String layout, long now)
            throws SQLException {
        // REPLACE INTO is valid on both SQLite and MySQL for these primary keys.
        try (PreparedStatement ps = c.prepareStatement(
                "REPLACE INTO fc_hud_presets (uuid, slot, name, layout, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.setString(3, name);
            ps.setString(4, layout);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO fc_hud_active (uuid, slot) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.executeUpdate();
        }
    }
}
