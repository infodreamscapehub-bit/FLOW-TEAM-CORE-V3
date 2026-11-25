package com.flowcity.core.player;

import com.flowcity.core.database.DatabaseManager;
import com.flowcity.core.id.IdSettings;
import com.flowcity.core.text.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Owns the in-memory state of online players: UUID (internal identity) and RP ID (public identity).
 *
 * The RP ID is loaded - or allocated on first join - in AsyncPlayerPreLoginEvent, which runs on a
 * network thread, so the database work never blocks the main thread. If the database does not
 * answer, the login is refused instead of letting a player in without an ID.
 */
public final class PlayerService implements Listener {

    /** Live data of one online player. */
    public static final class Session {
        public final UUID uuid;
        public final long firstJoin;
        private final long playtimeAtLogin;
        private final long loginAt;
        private volatile int rpId;
        private volatile String name;

        Session(PlayerRecord r, long now) {
            this.uuid = r.uuid;
            this.firstJoin = r.firstJoin;
            this.playtimeAtLogin = r.playtimeSeconds;
            this.loginAt = now;
            this.rpId = r.rpId;
            this.name = r.lastName;
        }

        public int rpId() {
            return rpId;
        }

        public String name() {
            return name;
        }

        /** Stored playtime plus the current session. */
        public long playtimeSeconds() {
            return playtimeAtLogin + Math.max(0, (System.currentTimeMillis() - loginAt) / 1000L);
        }
    }

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final PlayerRepository repo;
    private final Supplier<IdSettings> settings;
    private final Messages messages;

    private final Map<UUID, PlayerRecord> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> onlineByRpId = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> idChangeListeners = new CopyOnWriteArrayList<>();
    private BukkitTask flushTask;

    public PlayerService(JavaPlugin plugin, DatabaseManager db, PlayerRepository repo,
                         Supplier<IdSettings> settings, Messages messages) {
        this.plugin = plugin;
        this.db = db;
        this.repo = repo;
        this.settings = settings;
        this.messages = messages;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Players already online (plugin reload): load them asynchronously.
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            String name = p.getName();
            db.transaction(c -> repo.loadOrCreate(c, uuid, name, System.currentTimeMillis(), settings.get().rules))
                    .whenComplete((rec, err) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Player online = plugin.getServer().getPlayer(uuid);
                        if (online == null) {
                            return;
                        }
                        if (err != null) {
                            plugin.getLogger().log(Level.SEVERE, "Could not load " + name, err);
                            online.kickPlayer(messages.plain("general.login-database-error"));
                            return;
                        }
                        register(rec);
                    }));
        }
        long period = settings.get().playtimeSaveMinutes * 60L * 20L;
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushAll, period, period);
    }

    /** Saves every online player and waits (max 10 s). Called on plugin disable, before the pool closes. */
    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        HandlerList.unregisterAll(this);
        List<CompletableFuture<?>> pending = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            pending.add(save(s, now));
        }
        try {
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save all players on shutdown", e);
        }
        sessions.clear();
        onlineByRpId.clear();
        preloaded.clear();
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        try {
            PlayerRecord rec = db.transaction(c -> repo.loadOrCreate(c, uuid, name, System.currentTimeMillis(),
                    settings.get().rules)).get(10, TimeUnit.SECONDS);
            preloaded.put(uuid, rec);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load/create RP ID for " + name + " (" + uuid + ")", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.plain("general.login-database-error"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            preloaded.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerRecord rec = preloaded.remove(p.getUniqueId());
        if (rec == null) {
            plugin.getLogger().severe("No FlowCity data for " + p.getName() + " at join; kicking to stay consistent");
            p.kickPlayer(messages.plain("general.login-database-error"));
            return;
        }
        register(rec);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Session s = sessions.remove(event.getPlayer().getUniqueId());
        if (s != null) {
            onlineByRpId.remove(s.rpId, s.uuid);
            save(s, System.currentTimeMillis());
        }
    }

    // ------------------------------------------------------------------ API

    public Session session(UUID uuid) {
        return sessions.get(uuid);
    }

    public Session session(Player p) {
        return sessions.get(p.getUniqueId());
    }

    /** Online player with this RP ID, or null. */
    public Player onlineByRpId(int rpId) {
        UUID uuid = onlineByRpId.get(rpId);
        return uuid == null ? null : plugin.getServer().getPlayer(uuid);
    }

    public Collection<Session> sessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public CompletableFuture<PlayerRecord> findByRpId(int rpId) {
        return db.query(c -> repo.findByRpId(c, rpId));
    }

    public CompletableFuture<PlayerRecord> findByName(String name) {
        return db.query(c -> repo.findByName(c, name));
    }

    /** Called on the main thread after a player's RP ID changed. */
    public void onIdChange(Consumer<Player> listener) {
        idChangeListeners.add(listener);
    }

    /** /setid. Completes with the old ID; fails with IdChangeException for rule violations. */
    public CompletableFuture<Integer> changeId(UUID target, int newId, UUID admin, String reason) {
        IdRules rules = settings.get().rules;
        return db.transaction(c -> repo.changeId(c, target, newId, admin, reason, System.currentTimeMillis(), rules))
                .thenApplyAsync(old -> {
                    applyIdChange(target, old, newId);
                    return old;
                }, mainThread());
    }

    /** /resetid. Completes with {old, new}. */
    public CompletableFuture<int[]> resetId(UUID target, UUID admin, String reason) {
        IdRules rules = settings.get().rules;
        return db.transaction(c -> repo.resetId(c, target, admin, reason, System.currentTimeMillis(), rules))
                .thenApplyAsync(ids -> {
                    applyIdChange(target, ids[0], ids[1]);
                    return ids;
                }, mainThread());
    }

    // ------------------------------------------------------------------ internals

    private void register(PlayerRecord rec) {
        Session s = new Session(rec, System.currentTimeMillis());
        sessions.put(rec.uuid, s);
        onlineByRpId.put(rec.rpId, rec.uuid);
    }

    private void applyIdChange(UUID uuid, int oldId, int newId) {
        Session s = sessions.get(uuid);
        if (s == null) {
            return;
        }
        onlineByRpId.remove(oldId, uuid);
        s.rpId = newId;
        onlineByRpId.put(newId, uuid);
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) {
            for (Consumer<Player> l : idChangeListeners) {
                l.accept(p);
            }
        }
    }

    private void flushAll() {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            save(s, now);
        }
    }

    private CompletableFuture<Object> save(Session s, long now) {
        String name = s.name;
        long total = s.playtimeSeconds();
        return db.transaction(c -> {
            repo.saveSession(c, s.uuid, name, now, total);
            return null;
        }).exceptionally(err -> {
            plugin.getLogger().log(Level.SEVERE, "Could not save player " + name, err);
            return null;
        });
    }

    private java.util.concurrent.Executor mainThread() {
        return task -> {
            if (plugin.getServer().isPrimaryThread()) {
                task.run();
            } else {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
    }
}
