package com.flowcity.core.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Ranks from the official LuckPerms API. FlowCity never stores ranks itself.
 * This class is only loaded when the LuckPerms plugin is present.
 */
public final class LuckPermsRankService implements RankService {

    private final Plugin plugin;
    private final LuckPerms luckPerms;
    private final EventSubscription<UserDataRecalculateEvent> subscription;
    private final List<Consumer<UUID>> listeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, RankDisplay> lastKnown = new ConcurrentHashMap<>();
    private volatile RankSettings settings;

    public LuckPermsRankService(Plugin plugin, RankSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.luckPerms = LuckPermsProvider.get();
        // Fired (async) whenever a user's permission data changes: group set, temp rank expiry, etc.
        this.subscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            plugin.getServer().getScheduler().runTask(plugin, () -> recheck(uuid));
        });
    }

    @Override
    public RankDisplay rankOf(Player player) {
        RankDisplay d = compute(player.getUniqueId());
        lastKnown.put(player.getUniqueId(), d);
        return d;
    }

    @Override
    public java.util.concurrent.CompletableFuture<RankDisplay> rankOf(UUID uuid) {
        User loaded = luckPerms.getUserManager().getUser(uuid);
        if (loaded != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(compute(loaded));
        }
        return luckPerms.getUserManager().loadUser(uuid).thenApply(this::compute);
    }

    private RankDisplay compute(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        return user == null ? settings.fallback : compute(user);
    }

    private RankDisplay compute(User user) {
        List<String> inherited = new ArrayList<>();
        for (Group g : user.getInheritedGroups(user.getQueryOptions())) {
            inherited.add(g.getName());
        }
        return settings.resolve(user.getPrimaryGroup(), inherited.isEmpty()
                ? Collections.singletonList(user.getPrimaryGroup()) : inherited);
    }

    private void recheck(UUID uuid) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p == null) {
            lastKnown.remove(uuid);
            return;
        }
        RankDisplay now = compute(uuid);
        RankDisplay before = lastKnown.put(uuid, now);
        if (!now.equals(before)) {
            for (Consumer<UUID> l : listeners) {
                l.accept(uuid);
            }
        }
    }

    @Override
    public void forget(UUID uuid) {
        lastKnown.remove(uuid);
    }

    @Override
    public void onRankChange(Consumer<UUID> listener) {
        listeners.add(listener);
    }

    @Override
    public void reload(RankSettings settings) {
        this.settings = settings;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            recheck(p.getUniqueId());
        }
    }

    @Override
    public String describe() {
        return "LuckPerms " + luckPerms.getPluginMetadata().getVersion() + " (" + settings.mode + ")";
    }

    @Override
    public void shutdown() {
        subscription.close();
        lastKnown.clear();
    }
}
