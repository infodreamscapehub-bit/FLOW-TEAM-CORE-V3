package com.flowcity.core.player;

import com.flowcity.common.protocol.packets.PlayerStatePacket;
import com.flowcity.core.id.IdSettings;
import com.flowcity.core.protocol.ClientConnectionService;
import com.flowcity.core.rank.RankDisplay;
import com.flowcity.core.rank.RankService;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * Sends a player their own HUD data (RP ID, name, rank, playtime). Pushed on handshake,
 * RP ID change and rank change - never polled, never trusted back from the client.
 */
public final class HudStateService {

    private final PlayerService players;
    private final RankService ranks;
    private final ClientConnectionService connections;
    private final Supplier<IdSettings> settings;

    public HudStateService(PlayerService players, RankService ranks, ClientConnectionService connections,
                           Supplier<IdSettings> settings) {
        this.players = players;
        this.ranks = ranks;
        this.connections = connections;
        this.settings = settings;
    }

    public void push(Player p) {
        PlayerService.Session s = players.session(p);
        if (s == null || !connections.isConfirmed(p)) {
            return;
        }
        RankDisplay rank = ranks.rankOf(p);
        // Character names arrive with the character system; until then the HUD shows the account name.
        connections.send(p, new PlayerStatePacket(s.rpId(), settings.get().format(s.rpId()), p.getName(),
                rank.display, rank.color, rank.icon, s.playtimeSeconds()).encode());
    }
}
