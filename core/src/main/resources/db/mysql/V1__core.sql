-- V1: player identity, permanent RP ID allocation, ID history, admin audit log.

CREATE TABLE IF NOT EXISTS fc_players (
    uuid             CHAR(36)    NOT NULL PRIMARY KEY,
    rp_id            INT         NOT NULL,
    last_name        VARCHAR(16) NOT NULL,
    first_join       BIGINT      NOT NULL,
    last_seen        BIGINT      NOT NULL,
    playtime_seconds BIGINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uq_fc_players_rp_id (rp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Monotonic counters. RP IDs are taken from here and never handed out twice,
-- even after a player is deleted or their ID is changed.
CREATE TABLE IF NOT EXISTS fc_counters (
    name       VARCHAR(32) NOT NULL PRIMARY KEY,
    next_value BIGINT      NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO fc_counters (name, next_value) VALUES ('rp_id', 1);

CREATE TABLE IF NOT EXISTS fc_rp_id_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid       CHAR(36)     NOT NULL,
    old_id     INT          NULL,
    new_id     INT          NOT NULL,
    changed_by CHAR(36)     NULL,
    reason     VARCHAR(255) NULL,
    changed_at BIGINT       NOT NULL,
    KEY idx_fc_rp_id_history_uuid (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fc_admin_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_uuid     CHAR(36)     NULL,
    target_uuid    CHAR(36)     NULL,
    action         VARCHAR(64)  NOT NULL,
    reason         VARCHAR(255) NULL,
    previous_value TEXT         NULL,
    new_value      TEXT         NULL,
    created_at     BIGINT       NOT NULL,
    KEY idx_fc_admin_log_target (target_uuid),
    KEY idx_fc_admin_log_admin (admin_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
