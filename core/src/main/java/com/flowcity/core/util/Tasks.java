package com.flowcity.core.util;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Hands results of async (database) work back to the server main thread. */
public final class Tasks {

    private Tasks() {
    }

    /**
     * When {@code future} completes, runs {@code onSuccess} on the main thread.
     * Failures are logged; they never reach gameplay code as an exception.
     */
    public static <T> void thenSync(Plugin plugin, CompletableFuture<T> future, Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Async task failed", error);
                return;
            }
            if (!plugin.isEnabled()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> onSuccess.accept(value));
        });
    }
}
