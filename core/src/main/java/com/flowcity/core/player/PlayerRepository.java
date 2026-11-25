package com.flowcity.core.player;

import com.flowcity.core.database.Dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * SQL for player identity and RP IDs. Every method takes the caller's connection, so callers
 * decide the transaction boundaries (always via DatabaseManager, never on the main thread).
 *
 * Uniqueness is guaranteed twice: by allocation logic and by the UNIQUE index on fc_players.rp_id.
 */
public final class PlayerRepository {

    private static final String COLUMNS = "uuid, rp_id, last_name, first_join, last_seen, playtime_seconds";

    private final Dialect dialect;

    public PlayerRepository(Dialect dialect) {
        this.dialect = dialect;
    }

    public PlayerRecord findByUuid(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM fc_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return readOne(ps);
        }
    }

    public PlayerRecord findByRpId(Connection c, int rpId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM fc_players WHERE rp_id = ?")) {
            ps.setInt(1, rpId);
            return readOne(ps);
        }
    }

    /** Case-insensitive lookup by last known Minecraft name (staff tools only). */
    public PlayerRecord findByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM fc_players WHERE LOWER(last_name) = LOWER(?) ORDER BY last_seen DESC")) {
            ps.setString(1, name);
            return readOne(ps);
        }
    }

    /** Loads the player, creating them with a fresh RP ID on first join. Must run in a transaction. */
    public PlayerRecord loadOrCreate(Connection c, UUID uuid, String name, long now, IdRules rules)
            throws SQLException, IdChangeException {
        PlayerRecord existing = findByUuid(c, uuid);
        if (existing != null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE fc_players SET last_name = ?, last_seen = ? WHERE uuid = ?")) {
                ps.setString(1, name);
                ps.setLong(2, now);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
            return new PlayerRecord(uuid, existing.rpId, name, existing.firstJoin, now, existing.playtimeSeconds);
        }
        int id = allocate(c, rules);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO fc_players (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, 0)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, id);
            ps.setString(3, name);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        insertHistory(c, uuid, null, id, null, "first join", now);
        return new PlayerRecord(uuid, id, name, now, now, 0);
    }

    /** Writes the end of a session (or a periodic flush). playtimeSeconds is the new total. */
    public void saveSession(Connection c, UUID uuid, String name, long lastSeen, long playtimeSeconds) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE fc_players SET last_name = ?, last_seen = ?, playtime_seconds = ? WHERE uuid = ?")) {
            ps.setString(1, name);
            ps.setLong(2, lastSeen);
            ps.setLong(3, playtimeSeconds);
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        }
    }

    /** Admin change to a specific ID (/setid). Returns the previous ID. Must run in a transaction. */
    public int changeId(Connection c, UUID uuid, int newId, UUID changedBy, String reason, long now, IdRules rules)
            throws SQLException, IdChangeException {
        PlayerRecord p = findByUuid(c, uuid);
        if (p == null) {
            throw new IdChangeException(IdChangeException.Reason.NOT_FOUND);
        }
        if (!rules.inRange(newId)) {
            throw new IdChangeException(IdChangeException.Reason.OUT_OF_RANGE);
        }
        if (p.rpId == newId) {
            throw new IdChangeException(IdChangeException.Reason.SAME);
        }
        if (isAssigned(c, newId)) {
            throw new IdChangeException(IdChangeException.Reason.TAKEN);
        }
        if (!rules.allowReuse && wasEverUsed(c, newId)) {
            throw new IdChangeException(IdChangeException.Reason.RETIRED);
        }
        updateId(c, uuid, newId);
        insertHistory(c, uuid, p.rpId, newId, changedBy, reason, now);
        return p.rpId;
    }

    /** Gives the player a brand-new ID from the counter (/resetid). Returns {old, new}. */
    public int[] resetId(Connection c, UUID uuid, UUID changedBy, String reason, long now, IdRules rules)
            throws SQLException, IdChangeException {
        PlayerRecord p = findByUuid(c, uuid);
        if (p == null) {
            throw new IdChangeException(IdChangeException.Reason.NOT_FOUND);
        }
        int newId = allocate(c, rules);
        updateId(c, uuid, newId);
        insertHistory(c, uuid, p.rpId, newId, changedBy, reason, now);
        return new int[]{p.rpId, newId};
    }

    /**
     * Next free ID from the monotonic counter. Skips IDs that are assigned or (unless reuse is
     * allowed) were ever used. On MySQL the counter row is locked, so concurrent joins serialise.
     */
    int allocate(Connection c, IdRules rules) throws SQLException, IdChangeException {
        String select = "SELECT next_value FROM fc_counters WHERE name = 'rp_id'"
                + (dialect == Dialect.MYSQL ? " FOR UPDATE" : "");
        long next;
        try (PreparedStatement ps = c.prepareStatement(select); ResultSet rs = ps.executeQuery()) {
            next = rs.next() ? rs.getLong(1) : 1L;
        }
        long candidate = Math.max(next, rules.startId);
        while (candidate <= rules.maxId) {
            int id = (int) candidate;
            if (!isAssigned(c, id) && (rules.allowReuse || !wasEverUsed(c, id))) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE fc_counters SET next_value = ? WHERE name = 'rp_id'")) {
                    ps.setLong(1, candidate + 1);
                    ps.executeUpdate();
                }
                return id;
            }
            candidate++;
        }
        throw new IdChangeException(IdChangeException.Reason.EXHAUSTED);
    }

    boolean isAssigned(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM fc_players WHERE rp_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    boolean wasEverUsed(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM fc_rp_id_history WHERE old_id = ? OR new_id = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void updateId(Connection c, UUID uuid, int newId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE fc_players SET rp_id = ? WHERE uuid = ?")) {
            ps.setInt(1, newId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void insertHistory(Connection c, UUID uuid, Integer oldId, int newId, UUID changedBy, String reason, long now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO fc_rp_id_history (uuid, old_id, new_id, changed_by, reason, changed_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            if (oldId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, oldId);
            }
            ps.setInt(3, newId);
            ps.setString(4, changedBy == null ? null : changedBy.toString());
            ps.setString(5, reason == null ? null : truncate(reason, 255));
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static PlayerRecord readOne(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new PlayerRecord(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getString(3),
                    rs.getLong(4), rs.getLong(5), rs.getLong(6));
        }
    }
}
