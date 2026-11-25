package com.flowcity.common.protocol.packets;

import com.flowcity.common.hud.HudLayout;
import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/** Serverbound: save a layout into a preset slot and make that slot active. The server validates everything. */
public final class HudSavePacket {

    public final int slot;
    public final String name;
    public final String layout;

    public HudSavePacket(int slot, String name, String layout) {
        this.slot = slot;
        this.name = name;
        this.layout = layout;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.SB_HUD_SAVE)
                .writeVarInt(slot)
                .writeString(name)
                .writeString(layout)
                .toByteArray();
    }

    public static HudSavePacket decode(FlowPacketReader r) throws ProtocolException {
        int slot = r.readVarInt();
        String name = r.readString(64);
        String layout = r.readString(HudLayout.MAX_ENCODED_LENGTH * 2);
        r.expectEnd();
        return new HudSavePacket(slot, name, layout);
    }
}
