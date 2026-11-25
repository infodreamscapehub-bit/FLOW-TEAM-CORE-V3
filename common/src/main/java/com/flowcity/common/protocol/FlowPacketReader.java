package com.flowcity.common.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Bounds-checked binary reader. Every read validates remaining length, so a hostile client
 * cannot trigger exceptions outside {@link ProtocolException} or huge allocations.
 */
public final class FlowPacketReader {

    private final byte[] data;
    private int pos;
    private final int protocolVersion;
    private final int packetId;

    private FlowPacketReader(byte[] data) throws ProtocolException {
        this.data = data;
        this.protocolVersion = readVarInt();
        this.packetId = readVarInt();
    }

    /**
     * Parses the header and checks the size limit and protocol version.
     *
     * @param maxBytes size limit for this direction (see {@link Protocol})
     */
    public static FlowPacketReader open(byte[] data, int maxBytes) throws ProtocolException {
        if (data == null || data.length == 0) {
            throw new ProtocolException("Empty payload");
        }
        if (data.length > maxBytes) {
            throw new ProtocolException("Payload too large: " + data.length + " > " + maxBytes);
        }
        FlowPacketReader r = new FlowPacketReader(data);
        if (r.protocolVersion != Protocol.VERSION) {
            throw new ProtocolException("Protocol mismatch: got " + r.protocolVersion + ", expected " + Protocol.VERSION);
        }
        return r;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public int packetId() {
        return packetId;
    }

    public int remaining() {
        return data.length - pos;
    }

    private void require(int n) throws ProtocolException {
        if (n < 0 || remaining() < n) {
            throw new ProtocolException("Unexpected end of payload (need " + n + ", have " + remaining() + ")");
        }
    }

    public int readVarInt() throws ProtocolException {
        int value = 0;
        int shift = 0;
        while (true) {
            require(1);
            int b = data[pos++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 35) {
                throw new ProtocolException("VarInt too long");
            }
        }
    }

    public long readLong() throws ProtocolException {
        require(8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (data[pos++] & 0xFFL);
        }
        return v;
    }

    public boolean readBoolean() throws ProtocolException {
        require(1);
        return data[pos++] != 0;
    }

    public String readString(int maxBytes) throws ProtocolException {
        int len = readVarInt();
        if (len < 0 || len > Math.min(maxBytes, Protocol.MAX_STRING_BYTES)) {
            throw new ProtocolException("String length out of range: " + len);
        }
        require(len);
        String s = new String(data, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    /** Call after reading a packet body: trailing bytes mean a malformed or tampered packet. */
    public void expectEnd() throws ProtocolException {
        if (remaining() != 0) {
            throw new ProtocolException("Trailing bytes: " + remaining());
        }
    }
}
