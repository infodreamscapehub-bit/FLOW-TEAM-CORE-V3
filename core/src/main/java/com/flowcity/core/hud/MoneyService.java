package com.flowcity.core.hud;

import com.flowcity.common.protocol.packets.MoneyStatePacket;
import com.flowcity.core.protocol.ClientConnectionService;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Pushes cash / bank balances to the HUD.
 *
 * Cash is the player's balance in the Vault economy provider (EssentialsX Economy or any other).
 * Vault is read through reflection so FlowCityCore starts and runs without Vault installed; the
 * Bank module (Phase 4) will replace {@link #bankCents} with real bank accounts. Until then the
 * bank value is always 0 - nothing is invented.
 *
 * Balances are only re-sent when they changed.
 */
public final class MoneyService {

    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final JavaPlugin plugin;
    private final ClientConnectionService connections;
    private final Map<UUID, Long> lastCash = new ConcurrentHashMap<>();
    private volatile String symbol = "$";
    private volatile int decimals = 0;
    private volatile int refreshSeconds = 5;
    private BukkitTask task;

    private Class<?> economyType;
    private Method getBalance;
    private boolean vaultFailureLogged;

    public MoneyService(JavaPlugin plugin, ClientConnectionService connections) {
        this.plugin = plugin;
        this.connections = connections;
    }

    public void configure(YamlConfiguration hud) {
        symbol = hud.getString("currency.symbol", "$");
        if (symbol.length() > 4) {
            symbol = symbol.substring(0, 4);
        }
        decimals = hud.getInt("currency.decimals", 0) >= 1 ? 2 : 0;
        refreshSeconds = Math.max(1, hud.getInt("hud.money-refresh-seconds", 5));
    }

    public int refreshSeconds() {
        return refreshSeconds;
    }

    public void start() {
        stop();
        long period = refreshSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                push(p, false);
            }
        }, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void forget(UUID uuid) {
        lastCash.remove(uuid);
    }

    /** @param force send even if the balance did not change (handshake, settings reload) */
    public void push(Player p, boolean force) {
        if (!connections.isConfirmed(p)) {
            return;
        }
        long cash = cashCents(p);
        Long previous = lastCash.put(p.getUniqueId(), cash);
        if (!force && previous != null && previous == cash) {
            return;
        }
        connections.send(p, new MoneyStatePacket(cash, bankCents(p), symbol, decimals).encode());
    }

    /** Cash in minor units, or 0 when no Vault economy is available. */
    private long cashCents(Player p) {
        try {
            Object economy = economy();
            if (economy == null) {
                return 0L;
            }
            Object balance = getBalance.invoke(economy, (OfflinePlayer) p);
            double value = balance instanceof Number ? ((Number) balance).doubleValue() : 0D;
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 0L;
            }
            return Math.round(Math.max(-9.0E13, Math.min(9.0E13, value)) * 100D);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!vaultFailureLogged) {
                vaultFailureLogged = true;
                plugin.getLogger().log(Level.WARNING, "Could not read the balance from Vault; HUD cash shows 0", e);
            }
            return 0L;
        }
    }

    /** Placeholder until the Bank module exists (Phase 4). */
    private long bankCents(Player p) {
        return 0L;
    }

    private Object economy() throws ReflectiveOperationException {
        if (economyType == null) {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                return null;
            }
            economyType = Class.forName(ECONOMY_CLASS, true, plugin.getServer().getPluginManager().getPlugin("Vault")
                    .getClass().getClassLoader());
            getBalance = economyType.getMethod("getBalance", OfflinePlayer.class);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        RegisteredServiceProvider<?> rsp = plugin.getServer().getServicesManager()
                .getRegistration((Class) economyType);
        return rsp == null ? null : rsp.getProvider();
    }
}
