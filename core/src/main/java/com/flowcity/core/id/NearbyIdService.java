package com.flowcity.core.id;

import com.flowcity.common.protocol.packets.IdVisibilityPacket;
import com.flowcity.common.protocol.packets.NearbyIdsPacket;
import com.flowcity.core.player.PlayerService;
import com.flowcity.core.protocol.ClientConnectionService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Server-authoritative source of the IDs a FlowCity client may draw above players (LEFT ALT).
 * Only players within render distance that the viewer can actually see are included, and a
 * packet is only sent when the list changed.
 */
public final class NearbyIdService {

    public static final String PERM_SEE = "flowcity.id";
    public static final String PERM_STAFF_COLOR = "flowcity.id.staffcolor";

    private final Plugin plugin;
    private final PlayerService players;
    private final ClientConnectionService connections;
    private final Supplier<IdSettings> settings;
    private final Map<UUID, Integer> lastSentHash = new ConcurrentHashMap<>();
    private final Set<UUID> alwaysShow = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private int runningInterval;

    public NearbyIdService(Plugin plugin, PlayerService players, ClientConnectionService connections,
                           Supplier<IdSettings> settings) {
        this.plugin = plugin;
        this.players = players;
        this.connections = connections;
        this.settings = settings;
    }

    public void start() {
        stop();
        runningInterval = settings.get().updateIntervalTicks;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, runningInterval, runningInterval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Re-reads the interval after /idreload. */
    public void reload() {
        if (runningInterval != settings.get().updateIntervalTicks) {
            start();
        }
        lastSentHash.clear();
    }

    /** /viewid without arguments. Returns the new state. */
    public boolean toggleAlwaysShow(Player p) {
        boolean on;
        if (alwaysShow.remove(p.getUniqueId())) {
            on = false;
        } else {
            alwaysShow.add(p.getUniqueId());
            on = true;
        }
        connections.send(p, new IdVisibilityPacket(on).encode());
        return on;
    }

    /** Handshake done: restore the /viewid state and force a fresh list. */
    public void onClientReady(Player p) {
        lastSentHash.remove(p.getUniqueId());
        if (alwaysShow.contains(p.getUniqueId())) {
            connections.send(p, new IdVisibilityPacket(true).encode());
        }
    }

    public void forget(UUID uuid) {
        lastSentHash.remove(uuid);
        alwaysShow.remove(uuid);
    }

    private void tick() {
        IdSettings s = settings.get();
        double maxSq = s.renderDistance * s.renderDistance;
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!connections.isConfirmed(viewer)) {
                continue;
            }
            List<NearbyIdsPacket.Entry> entries = viewer.hasPermission(PERM_SEE)
                    ? collect(viewer, s, maxSq) : new ArrayList<>();
            int hash = hash(entries);
            Integer last = lastSentHash.put(viewer.getUniqueId(), hash);
            if (last == null || last != hash) {
                connections.send(viewer, new NearbyIdsPacket(entries).encode());
            }
        }
    }

    private List<NearbyIdsPacket.Entry> collect(Player viewer, IdSettings s, double maxSq) {
        Location here = viewer.getLocation();
        List<Player> near = new ArrayList<>();
        for (Player other : viewer.getWorld().getPlayers()) {
            if (other == viewer || !viewer.canSee(other)) {
                continue;
            }
            if (s.hideSpectators && other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (s.hideInvisible && other.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                continue;
            }
            if (other.getLocation().distanceSquared(here) <= maxSq && players.session(other) != null) {
                near.add(other);
            }
        }
        near.sort(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(here)));
        List<NearbyIdsPacket.Entry> out = new ArrayList<>();
        for (Player other : near) {
            if (out.size() >= NearbyIdsPacket.MAX_ENTRIES) {
                break;
            }
            PlayerService.Session session = players.session(other);
            if (session == null) {
                continue;
            }
            int color = other.hasPermission(PERM_STAFF_COLOR) ? s.staffColor : s.color;
            out.add(new NearbyIdsPacket.Entry(other.getEntityId(), s.format(session.rpId()), color));
        }
        return out;
    }

    private static int hash(List<NearbyIdsPacket.Entry> entries) {
        int h = 1;
        for (NearbyIdsPacket.Entry e : entries) {
            h = 31 * h + e.entityId;
            h = 31 * h + e.label.hashCode();
            h = 31 * h + e.color;
        }
        return h;
    }
}
