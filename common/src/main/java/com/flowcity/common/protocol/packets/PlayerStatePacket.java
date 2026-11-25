package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/**
 * Clientbound: the receiver's own HUD data. Sent on handshake and whenever one of the values
 * changes (ID change, rank change). The client advances playtime locally between updates.
 */
public final class PlayerStatePacket {

    public final int rpId;
    public final String idLabel;
    public final String displayName;
    public final String rankName;
    public final int rankColor;
    public final String rankIcon;
    public final long playtimeSeconds;

    public PlayerStatePacket(int rpId, String idLabel, String displayName, String rankName, int rankColor,
                             String rankIcon, long playtimeSeconds) {
        this.rpId = rpId;
        this.idLabel = idLabel;
        this.displayName = displayName;
        this.rankName = rankName;
        this.rankColor = rankColor;
        this.rankIcon = rankIcon;
        this.playtimeSeconds = playtimeSeconds;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.CB_PLAYER_STATE)
                .writeVarInt(rpId)
                .writeString(idLabel)
                .writeString(displayName)
                .writeString(rankName)
                .writeVarInt(rankColor)
                .writeString(rankIcon)
                .writeLong(playtimeSeconds)
                .toByteArray();
    }

    public static PlayerStatePacket decode(FlowPacketReader r) throws ProtocolException {
        PlayerStatePacket p = new PlayerStatePacket(r.readVarInt(), r.readString(32), r.readString(64),
                r.readString(64), r.readVarInt(), r.readString(64), r.readLong());
        r.expectEnd();
        return p;
    }
}
