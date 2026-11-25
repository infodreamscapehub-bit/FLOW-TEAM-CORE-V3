package com.flowcity.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads one YAML file from plugins/FlowCityCore/, creating it from the jar's default on first run.
 * The jar copy is attached as defaults, so keys added in a newer version still have values
 * even if the admin's file is older.
 */
public final class ConfigFiles {

    private final JavaPlugin plugin;

    public ConfigFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource(name);
        if (defaults != null) {
            yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return yml;
    }
}
