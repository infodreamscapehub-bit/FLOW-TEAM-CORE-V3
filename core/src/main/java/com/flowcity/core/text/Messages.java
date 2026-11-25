package com.flowcity.core.text;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/** messages.yml access with &amp;-colour codes and {placeholder} replacement. */
public final class Messages {

    private volatile YamlConfiguration yml;

    public Messages(YamlConfiguration yml) {
        this.yml = yml;
    }

    public void reload(YamlConfiguration yml) {
        this.yml = yml;
    }

    /** Coloured text for a key; pairs are placeholder name/value, e.g. ("id", "007"). */
    public String get(String key, Object... pairs) {
        String raw = yml.getString(key);
        if (raw == null) {
            raw = "&c[missing message: " + key + "]";
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return color(raw);
    }

    /** Same as {@link #get} but without colour codes (for client screens and kick messages). */
    public String plain(String key, Object... pairs) {
        return ChatColor.stripColor(get(key, pairs));
    }

    public void send(CommandSender to, String key, Object... pairs) {
        to.sendMessage(prefix() + get(key, pairs));
    }

    public String prefix() {
        return color(yml.getString("prefix", "&6&lFLOWCITY &8| &r"));
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
