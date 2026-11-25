package com.flowcity.core.player;

import java.util.UUID;

/** One row of fc_players. Immutable snapshot. */
public final class PlayerRecord {

    public final UUID uuid;
    public final int rpId;
    public final String lastName;
    public final long firstJoin;
    public final long lastSeen;
    public final long playtimeSeconds;

    public PlayerRecord(UUID uuid, int rpId, String lastName, long firstJoin, long lastSeen, long playtimeSeconds) {
        this.uuid = uuid;
        this.rpId = rpId;
        this.lastName = lastName;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.playtimeSeconds = playtimeSeconds;
    }
}
