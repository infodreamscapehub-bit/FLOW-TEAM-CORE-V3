package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/**
 * Clientbound: a player profile for the profile screen. The server fills only the fields the
 * viewer is allowed to see; staff-only fields are empty strings for normal players.
 */
public final class ProfilePacket {

    public final boolean found;
    /** Error text when {@link #found} is false (already localised by the server). */
    public final String error;

    public final int rpId;
    public final String idLabel;
    public final String displayName;
    public final String rankName;
    public final int rankColor;
    public final long playtimeSeconds;
    public final boolean online;
    public final long firstJoinMillis;
    public final long lastSeenMillis;
    /** Staff-only; empty for normal viewers. */
    public final String username;
    /** Staff-only; empty for normal viewers. */
    public final String uuid;

    public ProfilePacket(int rpId, String idLabel, String displayName, String rankName, int rankColor,
                         long playtimeSeconds, boolean online, long firstJoinMillis, long lastSeenMillis,
                         String username, String uuid) {
        this.found = true;
        this.error = "";
        this.rpId = rpId;
        this.idLabel = idLabel;
        this.displayName = displayName;
        this.rankName = rankName;
        this.rankColor = rankColor;
        this.playtimeSeconds = playtimeSeconds;
        this.online = online;
        this.firstJoinMillis = firstJoinMillis;
        this.lastSeenMillis = lastSeenMillis;
        this.username = username;
        this.uuid = uuid;
    }

    private ProfilePacket(String error) {
        this.found = false;
        this.error = error;
        this.rpId = 0;
        this.idLabel = "";
        this.displayName = "";
        this.rankName = "";
        this.rankColor = 0;
        this.playtimeSeconds = 0;
        this.online = false;
        this.firstJoinMillis = 0;
        this.lastSeenMillis = 0;
        this.username = "";
        this.uuid = "";
    }

    public static ProfilePacket notFound(String error) {
        return new ProfilePacket(error);
    }

    public byte[] encode() {
        FlowPacketWriter w = FlowPacketWriter.create(PacketIds.CB_PROFILE).writeBoolean(found);
        if (!found) {
            return w.writeString(error).toByteArray();
        }
        return w.writeVarInt(rpId)
                .writeString(idLabel)
                .writeString(displayName)
                .writeString(rankName)
                .writeVarInt(rankColor)
                .writeLong(playtimeSeconds)
                .writeBoolean(online)
                .writeLong(firstJoinMillis)
                .writeLong(lastSeenMillis)
                .writeString(username)
                .writeString(uuid)
                .toByteArray();
    }

    public static ProfilePacket decode(FlowPacketReader r) throws ProtocolException {
        boolean found = r.readBoolean();
        if (!found) {
            String error = r.readString(256);
            r.expectEnd();
            return notFound(error);
        }
        ProfilePacket p = new ProfilePacket(r.readVarInt(), r.readString(32), r.readString(64), r.readString(64),
                r.readVarInt(), r.readLong(), r.readBoolean(), r.readLong(), r.readLong(),
                r.readString(32), r.readString(36));
        r.expectEnd();
        return p;
    }
}
