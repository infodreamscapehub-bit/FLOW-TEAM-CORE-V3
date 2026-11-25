-- V3: per-player HUD presets (3 slots) and the active slot.
CREATE TABLE IF NOT EXISTS fc_hud_presets (
    uuid       CHAR(36)    NOT NULL,
    slot       INTEGER     NOT NULL,
    name       VARCHAR(32) NOT NULL,
    layout     TEXT        NOT NULL,
    updated_at BIGINT      NOT NULL,
    PRIMARY KEY (uuid, slot)
);

CREATE TABLE IF NOT EXISTS fc_hud_active (
    uuid CHAR(36) NOT NULL PRIMARY KEY,
    slot INTEGER  NOT NULL
);
