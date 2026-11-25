package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Clientbound: the RP ID labels of players near the receiver. The server decides who is
 * included (distance, visibility, permissions); the client only draws what it receives.
 * Each packet replaces the previous list completely.
 */
public final class NearbyIdsPacket {

    public static final int MAX_ENTRIES = 256;

    public static final class Entry {
        /** Network entity id of the player, as the client knows it. */
        public final int entityId;
        /** Ready-to-draw label, e.g. "007". Never a Minecraft username. */
        public final String label;
        /** ARGB colour. */
        public final int color;

        public Entry(int entityId, String label, int color) {
            this.entityId = entityId;
            this.label = label;
            this.color = color;
        }
    }

    public final List<Entry> entries;

    public NearbyIdsPacket(List<Entry> entries) {
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many entries: " + entries.size());
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public byte[] encode() {
        FlowPacketWriter w = FlowPacketWriter.create(PacketIds.CB_NEARBY_IDS).writeVarInt(entries.size());
        for (Entry e : entries) {
            w.writeVarInt(e.entityId).writeString(e.label).writeVarInt(e.color);
        }
        return w.toByteArray();
    }

    public static NearbyIdsPacket decode(FlowPacketReader r) throws ProtocolException {
        int count = r.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new ProtocolException("Bad entry count " + count);
        }
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(r.readVarInt(), r.readString(32), r.readVarInt()));
        }
        r.expectEnd();
        return new NearbyIdsPacket(list);
    }
}
