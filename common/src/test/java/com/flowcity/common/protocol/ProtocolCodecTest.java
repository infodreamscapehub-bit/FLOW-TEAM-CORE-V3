package com.flowcity.common.protocol;

import com.flowcity.common.protocol.packets.HelloAckPacket;
import com.flowcity.common.protocol.packets.HelloPacket;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolCodecTest {

    @Test
    void helloRoundTrip() throws Exception {
        byte[] bytes = new HelloPacket(-1234567890123L, "0.1.0").encode();
        FlowPacketReader r = FlowPacketReader.open(bytes, Protocol.MAX_CLIENTBOUND_BYTES);
        assertEquals(PacketIds.CB_HELLO, r.packetId());
        HelloPacket p = HelloPacket.decode(r);
        assertEquals(-1234567890123L, p.nonce);
        assertEquals("0.1.0", p.serverVersion);
    }

    @Test
    void helloAckRoundTripWithUnicode() throws Exception {
        byte[] bytes = new HelloAckPacket(42L, "0.1.0-ăîșț").encode();
        FlowPacketReader r = FlowPacketReader.open(bytes, Protocol.MAX_SERVERBOUND_BYTES);
        assertEquals(PacketIds.SB_HELLO_ACK, r.packetId());
        HelloAckPacket p = HelloAckPacket.decode(r);
        assertEquals(42L, p.nonce);
        assertEquals("0.1.0-ăîșț", p.modVersion);
    }

    @Test
    void varIntBoundaries() throws Exception {
        int[] values = {0, 1, 127, 128, 255, 2097151, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        FlowPacketWriter w = FlowPacketWriter.create(99);
        for (int v : values) {
            w.writeVarInt(v);
        }
        FlowPacketReader r = FlowPacketReader.open(w.toByteArray(), Protocol.MAX_CLIENTBOUND_BYTES);
        for (int v : values) {
            assertEquals(v, r.readVarInt());
        }
        r.expectEnd();
    }

    @Test
    void rejectsWrongProtocolVersion() {
        byte[] bytes = FlowPacketWriter.create(PacketIds.CB_HELLO).toByteArray();
        bytes[0] = (byte) (Protocol.VERSION + 1);
        assertThrows(ProtocolException.class, () -> FlowPacketReader.open(bytes, 1024));
    }

    @Test
    void rejectsTruncatedPayload() throws Exception {
        byte[] full = new HelloPacket(7L, "x").encode();
        byte[] cut = Arrays.copyOf(full, full.length - 3);
        FlowPacketReader r = FlowPacketReader.open(cut, 1024);
        assertThrows(ProtocolException.class, () -> HelloPacket.decode(r));
    }

    @Test
    void rejectsTrailingBytes() throws Exception {
        byte[] full = new HelloPacket(7L, "x").encode();
        byte[] longer = Arrays.copyOf(full, full.length + 1);
        FlowPacketReader r = FlowPacketReader.open(longer, 1024);
        assertThrows(ProtocolException.class, () -> HelloPacket.decode(r));
    }

    @Test
    void rejectsOversizedPayload() {
        assertThrows(ProtocolException.class, () -> FlowPacketReader.open(new byte[2048], 1024));
    }

    @Test
    void rejectsHugeStringLength() throws Exception {
        byte[] bytes = FlowPacketWriter.create(1).writeVarInt(Integer.MAX_VALUE).toByteArray();
        FlowPacketReader r = FlowPacketReader.open(bytes, 1024);
        assertThrows(ProtocolException.class, () -> r.readString(64));
    }
}
