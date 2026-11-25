package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/** Serverbound handshake reply. */
public final class HelloAckPacket {

    public final long nonce;
    public final String modVersion;

    public HelloAckPacket(long nonce, String modVersion) {
        this.nonce = nonce;
        this.modVersion = modVersion;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.SB_HELLO_ACK)
                .writeLong(nonce)
                .writeString(modVersion)
                .toByteArray();
    }

    public static HelloAckPacket decode(FlowPacketReader r) throws ProtocolException {
        long nonce = r.readLong();
        String version = r.readString(64);
        r.expectEnd();
        return new HelloAckPacket(nonce, version);
    }
}
