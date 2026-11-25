package com.flowcity.common.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Minimal binary writer. No dependency on Netty/Minecraft so it works in both the plugin and the mod. */
public final class FlowPacketWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

    private FlowPacketWriter() {
    }

    /** Starts a packet with the standard header (protocol version + packet id). */
    public static FlowPacketWriter create(int packetId) {
        FlowPacketWriter w = new FlowPacketWriter();
        w.writeVarInt(Protocol.VERSION);
        w.writeVarInt(packetId);
        return w;
    }

    public FlowPacketWriter writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        return this;
    }

    public FlowPacketWriter writeLong(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) (value >>> shift) & 0xFF);
        }
        return this;
    }

    public FlowPacketWriter writeBoolean(boolean value) {
        out.write(value ? 1 : 0);
        return this;
    }

    public FlowPacketWriter writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Protocol.MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String too long for protocol: " + bytes.length + " bytes");
        }
        writeVarInt(bytes.length);
        out.write(bytes, 0, bytes.length);
        return this;
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
