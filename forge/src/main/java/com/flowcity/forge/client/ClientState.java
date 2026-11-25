package com.flowcity.forge.client;

import com.flowcity.common.hud.HudLayout;
import com.flowcity.common.protocol.packets.HudConfigPacket;
import com.flowcity.common.protocol.packets.MoneyStatePacket;
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
    private static long selfFirstAt;
    private static MoneyStatePacket money;
    private static int hudActiveSlot;
    private static final Map<Integer, HudConfigPacket.Preset> hudPresets = new HashMap<>();
    private static HudLayout hudLayout;
    private static int hudVersion;

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
        if (self == null) {
            selfFirstAt = System.currentTimeMillis();
        }
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

    /** Milliseconds since the first HUD data arrived this session (drives the fade-in). */
    public static long selfAgeMillis() {
        return self == null ? 0 : System.currentTimeMillis() - selfFirstAt;
    }

    public static void setMoney(MoneyStatePacket packet) {
        money = packet;
    }

    /** Own balance as the server sent it, or null before the first update. */
    public static MoneyStatePacket money() {
        return money;
    }

    public static void setHudConfig(HudConfigPacket packet) {
        hudPresets.clear();
        for (HudConfigPacket.Preset p : packet.presets) {
            try {
                HudLayout.parse(p.layout);
                hudPresets.put(p.slot, p);
            } catch (IllegalArgumentException ignored) {
                // a corrupt stored preset is ignored; the default layout is used instead
            }
        }
        hudActiveSlot = hudPresets.containsKey(packet.activeSlot) ? packet.activeSlot : 0;
        hudLayout = hudActiveSlot == 0 ? null : HudLayout.parse(hudPresets.get(hudActiveSlot).layout);
        hudVersion++;
    }

    /** The layout to draw: the active saved preset, or the FlowCity default. */
    public static HudLayout hudLayout() {
        if (hudLayout == null) {
            hudLayout = HudLayout.defaults();
        }
        return hudLayout;
    }

    public static int hudActiveSlot() {
        return hudActiveSlot;
    }

    public static HudConfigPacket.Preset hudPreset(int slot) {
        return hudPresets.get(slot);
    }

    /** Increments every time the server sent a HUD config (editor uses it to confirm a save). */
    public static int hudVersion() {
        return hudVersion;
    }

    public static void clear() {
        nearby = Collections.emptyMap();
        alwaysShowIds = false;
        self = null;
        selfFirstAt = 0;
        money = null;
        hudActiveSlot = 0;
        hudPresets.clear();
        hudLayout = null;
    }
}
