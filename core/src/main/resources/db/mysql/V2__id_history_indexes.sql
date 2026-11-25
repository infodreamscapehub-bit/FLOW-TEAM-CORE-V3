-- V2: fast "was this RP ID ever used?" checks (IDs are never reused by default).
CREATE INDEX idx_fc_rp_id_history_old ON fc_rp_id_history (old_id);
CREATE INDEX idx_fc_rp_id_history_new ON fc_rp_id_history (new_id);
CREATE INDEX idx_fc_players_name ON fc_players (last_name);
