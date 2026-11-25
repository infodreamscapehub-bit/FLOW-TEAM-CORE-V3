package com.flowcity.core.rank;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/** Read-only view of a player's rank. Implemented on top of LuckPerms when it is installed. */
public interface RankService {

    /** Current rank display of an online player. Main thread. */
    RankDisplay rankOf(Player player);

    /** Rank of any player, online or offline (may load data from LuckPerms storage). */
    java.util.concurrent.CompletableFuture<RankDisplay> rankOf(UUID uuid);

    /** Called (on the main thread) with the UUID of an online player whose rank changed. */
    void onRankChange(Consumer<UUID> listener);

    void reload(RankSettings settings);

    /** Drops cached data of a player who left. */
    default void forget(UUID uuid) {
    }

    /** e.g. "LuckPerms 5.4.102" or "none (fallback rank for everyone)". */
    String describe();

    void shutdown();
}
