package com.flowcity.common.protocol;

/**
 * Wire-level constants shared by FlowCityCore (server plugin) and FlowCityForge (client mod).
 *
 * Every packet on the {@link #CHANNEL} channel is:
 * <pre>
 *   varint protocolVersion
 *   varint packetId
 *   ...packet body
 * </pre>
 * The server is always authoritative; the client only ever sends requests/acks.
 */
public final class Protocol {

    /** Bump whenever any packet layout changes. Client and server must match exactly. */
    public static final int VERSION = 2;

    /** Plugin-messaging channel (namespace:path, lower case, valid on 1.13+). */
    public static final String CHANNEL_NAMESPACE = "flowcity";
    public static final String CHANNEL_PATH = "main";
    public static final String CHANNEL = CHANNEL_NAMESPACE + ":" + CHANNEL_PATH;

    /** Hard cap for one payload. Vanilla allows ~1 MiB server->client and 32 KiB client->server. */
    public static final int MAX_SERVERBOUND_BYTES = 32 * 1024;
    public static final int MAX_CLIENTBOUND_BYTES = 1024 * 1024;

    /** Upper bound for any string field, in UTF-8 bytes. */
    public static final int MAX_STRING_BYTES = 8 * 1024;

    private Protocol() {
    }
}
