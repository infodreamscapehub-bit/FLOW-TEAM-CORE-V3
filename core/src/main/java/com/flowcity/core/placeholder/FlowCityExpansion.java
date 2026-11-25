package com.flowcity.core.placeholder;

import com.flowcity.core.id.IdSettings;
import com.flowcity.core.player.PlayerService;
import com.flowcity.core.rank.RankDisplay;
import com.flowcity.core.rank.RankService;
import com.flowcity.core.util.TimeFormat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * PlaceholderAPI placeholders (only registered when PlaceholderAPI is installed):
 * %flowcity_id% (formatted), %flowcity_id_raw%, %flowcity_rank%, %flowcity_rank_colored%,
 * %flowcity_playtime%. Useful e.g. to show RP IDs in a tab-list plugin.
 */
public final class FlowCityExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final PlayerService players;
    private final RankService ranks;
    private final Supplier<IdSettings> settings;

    public FlowCityExpansion(Plugin plugin, PlayerService players, RankService ranks, Supplier<IdSettings> settings) {
        this.plugin = plugin;
        this.players = players;
        this.ranks = ranks;
        this.settings = settings;
    }

    @Override
    public String getIdentifier() {
        return "flowcity";
    }

    @Override
    public String getAuthor() {
        return "FlowCity Team";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        PlayerService.Session s = players.session(player);
        switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "id":
                return s == null ? "" : settings.get().format(s.rpId());
            case "id_raw":
                return s == null ? "" : String.valueOf(s.rpId());
            case "rank": {
                RankDisplay r = ranks.rankOf(player);
                return r.display;
            }
            case "rank_colored":
                return ranks.rankOf(player).chatColored();
            case "playtime":
                return s == null ? "" : TimeFormat.duration(s.playtimeSeconds());
            default:
                return null;
        }
    }
}
