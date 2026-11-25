package com.flowcity.core.id;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.function.Supplier;

/**
 * Server-side fallback for hiding vanilla name tags: every player joins a main-scoreboard team
 * whose name tag visibility is NEVER. FlowCity clients additionally hide name tags locally.
 * The tab list is not affected by team name tag visibility.
 *
 * Limitation: players who are given a different scoreboard by another plugin do not see this
 * team; with such plugins (e.g. TAB) disable use-scoreboard-team and hide name tags there.
 */
public final class NametagService {

    private final Plugin plugin;
    private final Supplier<IdSettings> settings;
    private String activeTeam;

    public NametagService(Plugin plugin, Supplier<IdSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** (Re)applies the configuration to all online players. Main thread. */
    public void apply() {
        IdSettings s = settings.get();
        boolean enabled = s.hideVanilla && s.useScoreboardTeam;
        if (activeTeam != null && (!enabled || !activeTeam.equals(s.teamName))) {
            removeTeam();
        }
        if (!enabled) {
            return;
        }
        Team team = team(s.teamName);
        activeTeam = s.teamName;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            team.addEntry(p.getName());
        }
    }

    public void onJoin(Player p) {
        if (activeTeam != null) {
            team(activeTeam).addEntry(p.getName());
        }
    }

    public void onQuit(Player p) {
        if (activeTeam != null) {
            Team t = board().getTeam(activeTeam);
            if (t != null) {
                t.removeEntry(p.getName());
            }
        }
    }

    public void shutdown() {
        removeTeam();
    }

    private void removeTeam() {
        if (activeTeam == null) {
            return;
        }
        Team t = board().getTeam(activeTeam);
        if (t != null) {
            t.unregister();
        }
        activeTeam = null;
    }

    private Team team(String name) {
        Scoreboard board = board();
        Team t = board.getTeam(name);
        if (t == null) {
            t = board.registerNewTeam(name);
        }
        t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return t;
    }

    private Scoreboard board() {
        return plugin.getServer().getScoreboardManager().getMainScoreboard();
    }
}
