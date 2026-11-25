package com.flowcity.core.audit;

import com.flowcity.core.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Append-only audit log of administrative actions (fc_admin_log): who, on whom, what,
 * when, why, previous value, new value. Written asynchronously; failures are logged loudly.
 */
public final class AdminLog {

    private final DatabaseManager db;
    private final Logger log;

    public AdminLog(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    /** @param admin null for console */
    public void record(UUID admin, UUID target, String action, String reason, String previous, String next) {
        long now = System.currentTimeMillis();
        log.info("[audit] " + (admin == null ? "CONSOLE" : admin) + " " + action + " " + target
                + " " + previous + " -> " + next + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")"));
        db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO fc_admin_log "
                    + "(admin_uuid, target_uuid, action, reason, previous_value, new_value, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, admin == null ? null : admin.toString());
                ps.setString(2, target == null ? null : target.toString());
                ps.setString(3, action);
                ps.setString(4, reason == null ? null : (reason.length() > 255 ? reason.substring(0, 255) : reason));
                ps.setString(5, previous);
                ps.setString(6, next);
                ps.setLong(7, now);
                return ps.executeUpdate();
            }
        }).exceptionally(err -> {
            log.log(Level.SEVERE, "AUDIT LOG WRITE FAILED for " + action + " on " + target, err);
            return 0;
        });
    }
}
