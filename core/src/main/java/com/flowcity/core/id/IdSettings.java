package com.flowcity.core.id;

import com.flowcity.core.player.IdRules;
import org.bukkit.configuration.ConfigurationSection;

import java.util.IllegalFormatException;

/** Parsed ids.yml. Immutable; replaced as a whole on /idreload. */
public final class IdSettings {

    public final IdRules rules;
    public final boolean allowReset;
    public final String displayFormat;

    public final boolean hideVanilla;
    public final boolean useScoreboardTeam;
    public final String teamName;

    public final double renderDistance;
    public final int updateIntervalTicks;
    public final boolean hideSpectators;
    public final boolean hideInvisible;
    public final int color;
    public final int staffColor;

    public final long profileCooldownMillis;
    public final String dateFormat;
    public final int playtimeSaveMinutes;

    public IdSettings(ConfigurationSection y) {
        this.rules = new IdRules(y.getInt("ids.start-id", 1), y.getInt("ids.max-id", 99999),
                y.getBoolean("ids.allow-reuse", false));
        this.allowReset = y.getBoolean("ids.allow-reset", true);
        String fmt = y.getString("ids.display-format", "%03d");
        try {
            String.format(fmt, 7);
        } catch (IllegalFormatException e) {
            throw new IllegalArgumentException("ids.display-format is not a valid number format: " + fmt);
        }
        this.displayFormat = fmt;

        this.hideVanilla = y.getBoolean("nametags.hide-vanilla", true);
        this.useScoreboardTeam = y.getBoolean("nametags.use-scoreboard-team", true);
        String team = y.getString("nametags.team-name", "fc_hidden");
        this.teamName = team.length() > 16 ? team.substring(0, 16) : team;

        this.renderDistance = Math.max(1, Math.min(64, y.getDouble("nearby.render-distance", 16)));
        this.updateIntervalTicks = Math.max(2, y.getInt("nearby.update-interval-ticks", 10));
        this.hideSpectators = y.getBoolean("nearby.hide-spectators", true);
        this.hideInvisible = y.getBoolean("nearby.hide-invisible", true);
        this.color = parseColor(y.getString("nearby.color", "FFFFFFFF"));
        this.staffColor = parseColor(y.getString("nearby.staff-color", "FFFFAA00"));

        this.profileCooldownMillis = Math.max(0, y.getLong("profile.cooldown-seconds", 1)) * 1000L;
        this.dateFormat = y.getString("profile.date-format", "dd.MM.yyyy HH:mm");
        this.playtimeSaveMinutes = Math.max(1, y.getInt("playtime.save-interval-minutes", 5));
    }

    public String format(int rpId) {
        return String.format(displayFormat, rpId);
    }

    /** "FFAA00" (opaque) or "80FFAA00" (with alpha) to ARGB. */
    public static int parseColor(String hex) {
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        long v = Long.parseLong(h, 16);
        if (h.length() <= 6) {
            v |= 0xFF000000L;
        }
        return (int) v;
    }
}
