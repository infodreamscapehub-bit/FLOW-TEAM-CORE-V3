package com.flowcity.core.rank;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

/** Used when LuckPerms is not installed: everyone gets the "default" group display (or fallback). */
public final class FallbackRankService implements RankService {

    private volatile RankSettings settings;

    public FallbackRankService(RankSettings settings) {
        this.settings = settings;
    }

    @Override
    public RankDisplay rankOf(Player player) {
        return settings.resolve("default", Collections.singletonList("default"));
    }

    @Override
    public java.util.concurrent.CompletableFuture<RankDisplay> rankOf(UUID uuid) {
        return java.util.concurrent.CompletableFuture.completedFuture(
                settings.resolve("default", Collections.singletonList("default")));
    }

    @Override
    public void onRankChange(Consumer<UUID> listener) {
        // ranks never change without LuckPerms
    }

    @Override
    public void reload(RankSettings settings) {
        this.settings = settings;
    }

    @Override
    public String describe() {
        return "LuckPerms not installed - everyone shows the default rank";
    }

    @Override
    public void shutdown() {
    }
}
