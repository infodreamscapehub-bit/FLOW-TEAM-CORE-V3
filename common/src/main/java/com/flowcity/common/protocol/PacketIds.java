package com.flowcity.common.protocol;

/** Packet identifiers. Clientbound (CB) = server to client, serverbound (SB) = client to server. */
public final class PacketIds {

    // --- handshake ---
    /** CB: server greets a client and sends a nonce the client must echo. */
    public static final int CB_HELLO = 0x00;
    /** SB: client echoes the nonce and reports its mod version. */
    public static final int SB_HELLO_ACK = 0x01;

    // --- player / ID system (Phase 2) ---
    /** CB: RP IDs of players near the receiver (entity id -> label). */
    public static final int CB_NEARBY_IDS = 0x02;
    /** CB: a player profile, answer to SB_PROFILE_REQUEST or /viewid. */
    public static final int CB_PROFILE = 0x03;
    /** CB: /viewid toggle - always show nearby IDs without holding the key. */
    public static final int CB_ID_VISIBILITY = 0x04;
    /** CB: the receiver's own state for the HUD (ID, name, rank, playtime). */
    public static final int CB_PLAYER_STATE = 0x05;

    /** SB: client asks for a profile by RP ID (0 = own profile). */
    public static final int SB_PROFILE_REQUEST = 0x11;

    private PacketIds() {
    }
}
