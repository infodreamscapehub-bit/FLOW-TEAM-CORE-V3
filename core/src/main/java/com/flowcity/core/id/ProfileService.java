package com.flowcity.core.id;

import com.flowcity.common.protocol.packets.ProfilePacket;
import com.flowcity.core.player.PlayerRecord;
import com.flowcity.core.player.PlayerService;
import com.flowcity.core.protocol.ClientConnectionService;
import com.flowcity.core.rank.RankDisplay;
import com.flowcity.core.rank.RankService;
import com.flowcity.core.text.Messages;
import com.flowcity.core.util.TimeFormat;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Builds player profiles by RP ID for F6 / F7 / /viewid. FlowCity clients get the profile
 * screen (ProfilePacket); everyone else gets chat lines. Staff-only fields (account name,
 * UUID) are filled only when the viewer has flowcity.id.admin.
 */
public final class ProfileService {

    public static final String PERM_VIEW = "flowcity.id.view";
    public static final String PERM_ADMIN = "flowcity.id.admin";

    private final Plugin plugin;
    private final PlayerService players;
    private final RankService ranks;
    private final ClientConnectionService connections;
    private final Messages messages;
    private final Supplier<IdSettings> settings;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    public ProfileService(Plugin plugin, PlayerService players, RankService ranks,
                          ClientConnectionService connections, Messages messages, Supplier<IdSettings> settings) {
        this.plugin = plugin;
        this.players = players;
        this.ranks = ranks;
        this.connections = connections;
        this.messages = messages;
        this.settings = settings;
    }

    /** rpId 0 = viewer's own profile. Main thread. */
    public void show(CommandSender viewer, int rpId) {
        if (viewer instanceof Player) {
            Player p = (Player) viewer;
            long now = System.currentTimeMillis();
            Long last = lastRequest.get(p.getUniqueId());
            if (last != null && now - last < settings.get().profileCooldownMillis) {
                messages.send(p, "general.cooldown");
                return;
            }
            lastRequest.put(p.getUniqueId(), now);
            if (rpId == 0) {
                PlayerService.Session own = players.session(p);
                if (own == null) {
                    return;
                }
                rpId = own.rpId();
            }
        } else if (rpId == 0) {
            messages.send(viewer, "general.players-only");
            return;
        }

        boolean own = viewer instanceof Player && players.session((Player) viewer) != null
                && players.session((Player) viewer).rpId() == rpId;
        if (!own && !viewer.hasPermission(PERM_VIEW)) {
            messages.send(viewer, "general.no-permission");
            return;
        }
        boolean staff = viewer.hasPermission(PERM_ADMIN);
        int id = rpId;

        Player online = players.onlineByRpId(id);
        if (online != null) {
            PlayerService.Session s = players.session(online);
            RankDisplay rank = ranks.rankOf(online);
            deliver(viewer, new ProfilePacket(id, settings.get().format(id), online.getName(), rank.display, rank.color,
                    s.playtimeSeconds(), true, s.firstJoin, System.currentTimeMillis(),
                    staff ? online.getName() : "", staff ? online.getUniqueId().toString() : ""), rank);
            return;
        }

        UUID viewerId = viewer instanceof Player ? ((Player) viewer).getUniqueId() : null;
        players.findByRpId(id).thenCompose(rec -> {
            if (rec == null) {
                return CompletableFuture.completedFuture(new Object[]{null, null});
            }
            return ranks.rankOf(rec.uuid).thenApply(rank -> new Object[]{rec, rank});
        }).whenComplete((pair, err) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            CommandSender target = viewerId == null ? viewer : plugin.getServer().getPlayer(viewerId);
            if (target == null) {
                return;
            }
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Profile lookup failed for ID " + id, err);
                messages.send(target, "general.database-error");
                return;
            }
            PlayerRecord rec = (PlayerRecord) pair[0];
            if (rec == null) {
                deliver(target, ProfilePacket.notFound(messages.plain("id.profile-not-found", "id", id)), null);
                return;
            }
            RankDisplay rank = (RankDisplay) pair[1];
            deliver(target, new ProfilePacket(id, settings.get().format(id), rec.lastName, rank.display, rank.color,
                    rec.playtimeSeconds, false, rec.firstJoin, rec.lastSeen,
                    staff ? rec.lastName : "", staff ? rec.uuid.toString() : ""), rank);
        }));
    }

    public void forget(UUID uuid) {
        lastRequest.remove(uuid);
    }

    private void deliver(CommandSender viewer, ProfilePacket profile, RankDisplay rank) {
        if (viewer instanceof Player && connections.send((Player) viewer, profile.encode())) {
            return;
        }
        if (!profile.found) {
            viewer.sendMessage(messages.prefix() + Messages.color("&c") + profile.error);
            return;
        }
        String fmt = settings.get().dateFormat;
        viewer.sendMessage(messages.get("id.profile-header", "id", profile.idLabel));
        viewer.sendMessage(messages.get("id.profile-name", "name", profile.displayName));
        viewer.sendMessage(messages.get("id.profile-rank", "rank", rank == null ? "-" : rank.chatColored()));
        viewer.sendMessage(messages.get("id.profile-playtime", "playtime", TimeFormat.duration(profile.playtimeSeconds)));
        viewer.sendMessage(profile.online ? messages.get("id.profile-status-online")
                : messages.get("id.profile-status-offline", "lastseen", TimeFormat.date(profile.lastSeenMillis, fmt)));
        viewer.sendMessage(messages.get("id.profile-first-join", "firstjoin", TimeFormat.date(profile.firstJoinMillis, fmt)));
        if (!profile.username.isEmpty()) {
            viewer.sendMessage(messages.get("id.profile-username", "username", profile.username));
            viewer.sendMessage(messages.get("id.profile-uuid", "uuid", profile.uuid));
        }
    }
}
