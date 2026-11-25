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
    void nearbyIdsRoundTrip() throws Exception {
        java.util.List<com.flowcity.common.protocol.packets.NearbyIdsPacket.Entry> list = new java.util.ArrayList<>();
        list.add(new com.flowcity.common.protocol.packets.NearbyIdsPacket.Entry(12, "007", 0xFFFFA500));
        list.add(new com.flowcity.common.protocol.packets.NearbyIdsPacket.Entry(99, "1024", 0xFFFFFFFF));
        byte[] bytes = new com.flowcity.common.protocol.packets.NearbyIdsPacket(list).encode();
        FlowPacketReader r = FlowPacketReader.open(bytes, Protocol.MAX_CLIENTBOUND_BYTES);
        assertEquals(PacketIds.CB_NEARBY_IDS, r.packetId());
        com.flowcity.common.protocol.packets.NearbyIdsPacket p = com.flowcity.common.protocol.packets.NearbyIdsPacket.decode(r);
        assertEquals(2, p.entries.size());
        assertEquals(12, p.entries.get(0).entityId);
        assertEquals("007", p.entries.get(0).label);
        assertEquals(0xFFFFA500, p.entries.get(0).color);
        assertEquals("1024", p.entries.get(1).label);
    }

    @Test
    void profileRoundTripFoundAndNotFound() throws Exception {
        com.flowcity.common.protocol.packets.ProfilePacket in = new com.flowcity.common.protocol.packets.ProfilePacket(
                7, "007", "Fabian", "ADMIN", 0xFFFF5555, 7200L, true, 1L, 2L, "", "");
        FlowPacketReader r = FlowPacketReader.open(in.encode(), Protocol.MAX_CLIENTBOUND_BYTES);
        com.flowcity.common.protocol.packets.ProfilePacket out = com.flowcity.common.protocol.packets.ProfilePacket.decode(r);
        assertTrue(out.found);
        assertEquals(7, out.rpId);
        assertEquals("ADMIN", out.rankName);
        assertEquals(7200L, out.playtimeSeconds);

        FlowPacketReader r2 = FlowPacketReader.open(
                com.flowcity.common.protocol.packets.ProfilePacket.notFound("Nu exista").encode(), 1024);
        com.flowcity.common.protocol.packets.ProfilePacket nf = com.flowcity.common.protocol.packets.ProfilePacket.decode(r2);
        assertFalse(nf.found);
        assertEquals("Nu exista", nf.error);
    }

    @Test
    void playerStateAndRequestRoundTrip() throws Exception {
        com.flowcity.common.protocol.packets.PlayerStatePacket s = new com.flowcity.common.protocol.packets.PlayerStatePacket(
                7, "007", "Fabian", "ADMIN", 0xFFFF5555, "admin", 3600L);
        com.flowcity.common.protocol.packets.PlayerStatePacket s2 = com.flowcity.common.protocol.packets.PlayerStatePacket.decode(
                FlowPacketReader.open(s.encode(), Protocol.MAX_CLIENTBOUND_BYTES));
        assertEquals("Fabian", s2.displayName);
        assertEquals(3600L, s2.playtimeSeconds);

        com.flowcity.common.protocol.packets.ProfileRequestPacket q = com.flowcity.common.protocol.packets.ProfileRequestPacket.decode(
                FlowPacketReader.open(new com.flowcity.common.protocol.packets.ProfileRequestPacket(42).encode(), Protocol.MAX_SERVERBOUND_BYTES));
        assertEquals(42, q.rpId);

        byte[] negative = FlowPacketWriter.create(PacketIds.SB_PROFILE_REQUEST).writeVarInt(-5).toByteArray();
        assertThrows(ProtocolException.class, () -> com.flowcity.common.protocol.packets.ProfileRequestPacket.decode(
                FlowPacketReader.open(negative, 1024)));
    }

    @Test
    void rejectsHugeStringLength() throws Exception {
        byte[] bytes = FlowPacketWriter.create(1).writeVarInt(Integer.MAX_VALUE).toByteArray();
        FlowPacketReader r = FlowPacketReader.open(bytes, 1024);
        assertThrows(ProtocolException.class, () -> r.readString(64));
    }
}
