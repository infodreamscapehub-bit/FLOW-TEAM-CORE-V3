package com.flowcity.common.protocol;

/** Packet identifiers. Clientbound = server to client, serverbound = client to server. */
public final class PacketIds {

    // --- handshake (Phase 1) ---
    /** Clientbound: server greets a client and sends a nonce the client must echo. */
    public static final int CB_HELLO = 0x00;
    /** Serverbound: client echoes the nonce and reports its protocol + mod version. */
    public static final int SB_HELLO_ACK = 0x01;

    private PacketIds() {
    }
}
