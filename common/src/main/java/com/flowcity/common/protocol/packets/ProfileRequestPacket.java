package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/** Serverbound: F6 search / F7 profile. rpId 0 means "my own profile". */
public final class ProfileRequestPacket {

    public final int rpId;

    public ProfileRequestPacket(int rpId) {
        this.rpId = rpId;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.SB_PROFILE_REQUEST).writeVarInt(rpId).toByteArray();
    }

    public static ProfileRequestPacket decode(FlowPacketReader r) throws ProtocolException {
        int id = r.readVarInt();
        r.expectEnd();
        if (id < 0) {
            throw new ProtocolException("Negative RP ID");
        }
        return new ProfileRequestPacket(id);
    }
}
