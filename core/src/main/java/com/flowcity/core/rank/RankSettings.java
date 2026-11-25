package com.flowcity.core.rank;

import com.flowcity.core.id.IdSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Parsed ranks.yml: LuckPerms group name to display. */
public final class RankSettings {

    public enum Mode { PRIMARY_GROUP, HIGHEST_PRIORITY }

    public final Mode mode;
    public final RankDisplay fallback;
    private final Map<String, RankDisplay> groups;

    public RankSettings(ConfigurationSection y) {
        this.mode = "primary-group".equalsIgnoreCase(y.getString("mode", "highest-priority"))
                ? Mode.PRIMARY_GROUP : Mode.HIGHEST_PRIORITY;
        this.fallback = new RankDisplay("", y.getString("fallback.display", "CIVIL"),
                IdSettings.parseColor(y.getString("fallback.color", "FFBBBBBB")),
                y.getString("fallback.icon", "civil"), Integer.MIN_VALUE);
        Map<String, RankDisplay> map = new HashMap<>();
        ConfigurationSection g = y.getConfigurationSection("groups");
        if (g != null) {
            for (String name : g.getKeys(false)) {
                String key = name.toLowerCase(Locale.ROOT);
                map.put(key, new RankDisplay(key,
                        g.getString(name + ".display", name.toUpperCase(Locale.ROOT)),
                        IdSettings.parseColor(g.getString(name + ".color", "FFFFFFFF")),
                        g.getString(name + ".icon", key),
                        g.getInt(name + ".priority", 0)));
            }
        }
        this.groups = Collections.unmodifiableMap(map);
    }

    /**
     * @param primaryGroup  LuckPerms primary group (may be null)
     * @param inherited     all groups the player inherits (used in highest-priority mode)
     */
    public RankDisplay resolve(String primaryGroup, Collection<String> inherited) {
        if (mode == Mode.PRIMARY_GROUP) {
            RankDisplay d = primaryGroup == null ? null : groups.get(primaryGroup.toLowerCase(Locale.ROOT));
            return d != null ? d : fallback;
        }
        RankDisplay best = null;
        for (String name : inherited) {
            RankDisplay d = groups.get(name.toLowerCase(Locale.ROOT));
            if (d != null && (best == null || d.priority > best.priority)) {
                best = d;
            }
        }
        if (best == null && primaryGroup != null) {
            best = groups.get(primaryGroup.toLowerCase(Locale.ROOT));
        }
        return best != null ? best : fallback;
    }
}
