package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/** Clientbound handshake. The nonce is random per player session and must be echoed back. */
public final class HelloPacket {

    public final long nonce;
    public final String serverVersion;

    public HelloPacket(long nonce, String serverVersion) {
        this.nonce = nonce;
        this.serverVersion = serverVersion;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.CB_HELLO)
                .writeLong(nonce)
                .writeString(serverVersion)
                .toByteArray();
    }

    /** Reader must already be positioned after the header (packetId == CB_HELLO). */
    public static HelloPacket decode(FlowPacketReader r) throws ProtocolException {
        long nonce = r.readLong();
        String version = r.readString(64);
        r.expectEnd();
        return new HelloPacket(nonce, version);
    }
}
