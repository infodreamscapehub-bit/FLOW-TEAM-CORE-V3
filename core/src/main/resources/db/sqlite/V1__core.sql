-- V1: player identity, permanent RP ID allocation, ID history, admin audit log.

CREATE TABLE IF NOT EXISTS fc_players (
    uuid             CHAR(36)    NOT NULL PRIMARY KEY,
    rp_id            INTEGER     NOT NULL UNIQUE,
    last_name        VARCHAR(16) NOT NULL,
    first_join       BIGINT      NOT NULL,
    last_seen        BIGINT      NOT NULL,
    playtime_seconds BIGINT      NOT NULL DEFAULT 0
);

-- Monotonic counters. RP IDs are taken from here and never handed out twice,
-- even after a player is deleted or their ID is changed.
CREATE TABLE IF NOT EXISTS fc_counters (
    name       VARCHAR(32) NOT NULL PRIMARY KEY,
    next_value BIGINT      NOT NULL
);

INSERT OR IGNORE INTO fc_counters (name, next_value) VALUES ('rp_id', 1);

CREATE TABLE IF NOT EXISTS fc_rp_id_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid       CHAR(36)     NOT NULL,
    old_id     INTEGER,
    new_id     INTEGER      NOT NULL,
    changed_by CHAR(36),
    reason     VARCHAR(255),
    changed_at BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fc_rp_id_history_uuid ON fc_rp_id_history (uuid);

CREATE TABLE IF NOT EXISTS fc_admin_log (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_uuid     CHAR(36),
    target_uuid    CHAR(36),
    action         VARCHAR(64)  NOT NULL,
    reason         VARCHAR(255),
    previous_value TEXT,
    new_value      TEXT,
    created_at     BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fc_admin_log_target ON fc_admin_log (target_uuid);
CREATE INDEX IF NOT EXISTS idx_fc_admin_log_admin ON fc_admin_log (admin_uuid);
