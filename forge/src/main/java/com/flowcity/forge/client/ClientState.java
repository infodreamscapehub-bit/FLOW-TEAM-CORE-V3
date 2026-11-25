package com.flowcity.forge.client;

import com.flowcity.common.protocol.packets.NearbyIdsPacket;
import com.flowcity.common.protocol.packets.PlayerStatePacket;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything the client knows about FlowCity data, exactly as the server sent it.
 * Only touched on the client main thread. Cleared on disconnect.
 */
public final class ClientState {

    private static Map<Integer, NearbyIdsPacket.Entry> nearby = Collections.emptyMap();
    private static boolean alwaysShowIds;
    private static PlayerStatePacket self;
    private static long selfReceivedAt;

    private ClientState() {
    }

    public static void setNearby(NearbyIdsPacket packet) {
        Map<Integer, NearbyIdsPacket.Entry> map = new HashMap<>();
        for (NearbyIdsPacket.Entry e : packet.entries) {
            map.put(e.entityId, e);
        }
        nearby = map;
    }

    /** Label for a player entity, or null if the server did not send one. */
    public static NearbyIdsPacket.Entry idFor(int entityId) {
        return nearby.get(entityId);
    }

    public static boolean alwaysShowIds() {
        return alwaysShowIds;
    }

    public static void setAlwaysShowIds(boolean value) {
        alwaysShowIds = value;
    }

    public static void setSelf(PlayerStatePacket state) {
        self = state;
        selfReceivedAt = System.currentTimeMillis();
    }

    /** Own HUD data, or null before the server sent it. */
    public static PlayerStatePacket self() {
        return self;
    }

    /** Playtime advanced locally since the last server update. */
    public static long selfPlaytimeSeconds() {
        if (self == null) {
            return 0;
        }
        return self.playtimeSeconds + (System.currentTimeMillis() - selfReceivedAt) / 1000L;
    }

    public static void clear() {
        nearby = Collections.emptyMap();
        alwaysShowIds = false;
        self = null;
    }
}
