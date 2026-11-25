package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/** Clientbound: result of /viewid - when true the client shows nearby IDs without holding the key. */
public final class IdVisibilityPacket {

    public final boolean alwaysShow;

    public IdVisibilityPacket(boolean alwaysShow) {
        this.alwaysShow = alwaysShow;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.CB_ID_VISIBILITY).writeBoolean(alwaysShow).toByteArray();
    }

    public static IdVisibilityPacket decode(FlowPacketReader r) throws ProtocolException {
        boolean v = r.readBoolean();
        r.expectEnd();
        return new IdVisibilityPacket(v);
    }
}
