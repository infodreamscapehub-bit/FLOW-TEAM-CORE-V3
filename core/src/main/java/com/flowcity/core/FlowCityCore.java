package com.flowcity.core;

import com.flowcity.core.command.FlowCityCommand;
import com.flowcity.core.config.ConfigFiles;
import com.flowcity.core.database.DatabaseManager;
import com.flowcity.core.database.DatabaseSettingsLoader;
import com.flowcity.core.protocol.ClientConnectionService;
import com.flowcity.core.protocol.ForgeBridgeProbe;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class FlowCityCore extends JavaPlugin {

    private DatabaseManager database;
    private ClientConnectionService connections;
    private ForgeBridgeProbe bridgeProbe;

    @Override
    public void onEnable() {
        ConfigFiles files = new ConfigFiles(this);
        YamlConfiguration config = files.load("config.yml");
        YamlConfiguration databaseYml = files.load("database.yml");

        try {
            database = new DatabaseManager(getLogger(),
                    DatabaseSettingsLoader.load(databaseYml, getDataFolder()), getClassLoader());
            database.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Database start failed - FlowCityCore is disabled. Check database.yml.", e);
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bridgeProbe = ForgeBridgeProbe.run(getClassLoader());
        getLogger().info("Forge bridge probe: " + bridgeProbe.summary());

        connections = new ClientConnectionService(this,
                config.getInt("protocol.hello-delay-ticks"),
                config.getInt("protocol.hello-timeout-ticks"),
                config.getInt("protocol.max-packets-per-second"),
                config.getBoolean("debug"));
        connections.register();

        PluginCommand cmd = getCommand("flowcity");
        if (cmd != null) {
            FlowCityCommand executor = new FlowCityCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("FlowCityCore " + getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (connections != null) {
            connections.unregister();
            connections = null;
        }
        if (database != null) {
            database.shutdown();
            database = null;
        }
    }

    public DatabaseManager database() {
        return database;
    }

    public ClientConnectionService connections() {
        return connections;
    }

    public ForgeBridgeProbe bridgeProbe() {
        return bridgeProbe;
    }
}
