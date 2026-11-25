package com.flowcity.core;

import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.packets.ProfileRequestPacket;
import com.flowcity.core.audit.AdminLog;
import com.flowcity.core.command.FlowCityCommand;
import com.flowcity.core.config.ConfigFiles;
import com.flowcity.core.database.DatabaseManager;
import com.flowcity.core.database.DatabaseSettingsLoader;
import com.flowcity.core.gui.MenuListener;
import com.flowcity.core.id.IdCommands;
import com.flowcity.core.id.IdSettings;
import com.flowcity.core.id.NametagService;
import com.flowcity.core.id.NearbyIdService;
import com.flowcity.core.id.ProfileService;
import com.flowcity.core.placeholder.FlowCityExpansion;
import com.flowcity.core.player.HudStateService;
import com.flowcity.core.player.PlayerRepository;
import com.flowcity.core.player.PlayerService;
import com.flowcity.core.protocol.ClientConnectionService;
import com.flowcity.core.protocol.ForgeBridgeProbe;
import com.flowcity.core.rank.FallbackRankService;
import com.flowcity.core.rank.LuckPermsRankService;
import com.flowcity.core.rank.RankService;
import com.flowcity.core.rank.RankSettings;
import com.flowcity.core.text.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class FlowCityCore extends JavaPlugin implements Listener {

    private ConfigFiles files;
    private DatabaseManager database;
    private ClientConnectionService connections;
    private ForgeBridgeProbe bridgeProbe;

    private final AtomicReference<IdSettings> idSettings = new AtomicReference<>();
    private Messages messages;
    private RankService ranks;
    private PlayerService players;
    private HudStateService hud;
    private ProfileService profiles;
    private NearbyIdService nearby;
    private NametagService nametags;
    private FlowCityExpansion expansion;

    @Override
    public void onEnable() {
        files = new ConfigFiles(this);
        YamlConfiguration config = files.load("config.yml");
        YamlConfiguration databaseYml = files.load("database.yml");
        try {
            messages = new Messages(files.load("messages.yml"));
            idSettings.set(new IdSettings(files.load("ids.yml")));
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Invalid ids.yml / messages.yml - FlowCityCore is disabled", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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

        ranks = createRankService(new RankSettings(files.load("ranks.yml")));
        getLogger().info("Ranks: " + ranks.describe());

        players = new PlayerService(this, database, new PlayerRepository(database.dialect()), idSettings::get, messages);
        hud = new HudStateService(players, ranks, connections, idSettings::get);
        profiles = new ProfileService(this, players, ranks, connections, messages, idSettings::get);
        nearby = new NearbyIdService(this, players, connections, idSettings::get);
        nametags = new NametagService(this, idSettings::get);
        AdminLog adminLog = new AdminLog(database, getLogger());

        // Wiring: who reacts to what.
        connections.onClientReady(p -> {
            hud.push(p);
            nearby.onClientReady(p);
        });
        connections.registerHandler(PacketIds.SB_PROFILE_REQUEST,
                (p, reader) -> profiles.show(p, ProfileRequestPacket.decode(reader).rpId));
        players.onIdChange(hud::push);
        ranks.onRankChange(uuid -> {
            Player p = getServer().getPlayer(uuid);
            if (p != null) {
                hud.push(p);
            }
        });

        players.start();          // registers its join/quit listeners first (LOWEST / MONITOR)
        connections.register();
        nametags.apply();
        nearby.start();
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(this, this);

        IdCommands idCommands = new IdCommands(this, players, ranks, profiles, nearby, connections, adminLog,
                messages, idSettings::get, this::reloadIdSystem);
        for (String name : new String[]{"id", "listid", "viewid", "setid", "resetid", "idreload"}) {
            bind(name, idCommands);
        }
        bind("flowcity", new FlowCityCommand(this));

        registerPlaceholders();
        getLogger().info("FlowCityCore " + getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (connections != null) {
            connections.unregister();
        }
        if (nearby != null) {
            nearby.stop();
        }
        if (players != null) {
            players.stop();       // saves playtime of everyone online, before the pool closes
        }
        if (nametags != null) {
            nametags.shutdown();
        }
        if (ranks != null) {
            ranks.shutdown();
        }
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI already gone
            }
        }
        if (database != null) {
            database.shutdown();
            database = null;
        }
    }

    // ------------------------------------------------------------------ join / quit housekeeping

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        nametags.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        nametags.onQuit(p);
        nearby.forget(p.getUniqueId());
        profiles.forget(p.getUniqueId());
        ranks.forget(p.getUniqueId());
    }

    // ------------------------------------------------------------------ helpers

    private RankService createRankService(RankSettings settings) {
        Plugin lp = getServer().getPluginManager().getPlugin("LuckPerms");
        if (lp != null && lp.isEnabled()) {
            try {
                return new LuckPermsRankService(this, settings);
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "LuckPerms found but its API could not be used; using fallback ranks", t);
            }
        }
        return new FallbackRankService(settings);
    }

    private void registerPlaceholders() {
        Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return;
        }
        try {
            expansion = new FlowCityExpansion(this, players, ranks, idSettings::get);
            expansion.register();
            getLogger().info("PlaceholderAPI placeholders registered (%flowcity_id% ...)");
        } catch (Throwable t) {
            expansion = null;
            getLogger().log(Level.WARNING, "Could not register PlaceholderAPI placeholders", t);
        }
    }

    /** /idreload: re-reads ids.yml, ranks.yml, messages.yml. Throws (and keeps old values) if invalid. */
    private void reloadIdSystem() {
        IdSettings newIds = new IdSettings(files.load("ids.yml"));
        RankSettings newRanks = new RankSettings(files.load("ranks.yml"));
        messages.reload(files.load("messages.yml"));
        idSettings.set(newIds);
        ranks.reload(newRanks);
        nametags.apply();
        nearby.reload();
        for (Player p : getServer().getOnlinePlayers()) {
            hud.push(p);
        }
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command /" + name + " missing from plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
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

    public RankService ranks() {
        return ranks;
    }
}
