package com.flowcity.common.protocol.packets;

import com.flowcity.common.hud.HudLayout;
import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

import java.util.ArrayList;
import java.util.List;

/** Clientbound: the player's saved HUD presets and which slot is active. Empty list = use the default. */
public final class HudConfigPacket {

    public static final int MAX_SLOTS = 3;

    public static final class Preset {
        public final int slot;
        public final String name;
        public final String layout;

        public Preset(int slot, String name, String layout) {
            this.slot = slot;
            this.name = name;
            this.layout = layout;
        }
    }

    public final int activeSlot;
    public final List<Preset> presets;

    public HudConfigPacket(int activeSlot, List<Preset> presets) {
        this.activeSlot = activeSlot;
        this.presets = presets;
    }

    public byte[] encode() {
        FlowPacketWriter w = FlowPacketWriter.create(PacketIds.CB_HUD_CONFIG)
                .writeVarInt(activeSlot)
                .writeVarInt(presets.size());
        for (Preset p : presets) {
            w.writeVarInt(p.slot).writeString(p.name).writeString(p.layout);
        }
        return w.toByteArray();
    }

    public static HudConfigPacket decode(FlowPacketReader r) throws ProtocolException {
        int active = r.readVarInt();
        int count = r.readVarInt();
        if (count < 0 || count > MAX_SLOTS) {
            throw new ProtocolException("preset count out of range");
        }
        List<Preset> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slot = r.readVarInt();
            if (slot < 1 || slot > MAX_SLOTS) {
                throw new ProtocolException("slot out of range");
            }
            list.add(new Preset(slot, r.readString(64), r.readString(HudLayout.MAX_ENCODED_LENGTH * 2)));
        }
        r.expectEnd();
        return new HudConfigPacket(active, list);
    }
}
