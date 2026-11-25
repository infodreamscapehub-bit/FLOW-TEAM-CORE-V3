package com.flowcity.core.hud;

import com.flowcity.common.hud.HudLayout;
import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;
import com.flowcity.common.protocol.packets.HudConfigPacket;
import com.flowcity.common.protocol.packets.HudSavePacket;
import com.flowcity.core.database.DatabaseManager;
import com.flowcity.core.protocol.ClientConnectionService;
import com.flowcity.core.text.Messages;
import com.flowcity.core.util.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Stores each player's HUD presets and sends them to the client. The client only ever sends a
 * layout string for its own account; the server parses it, re-encodes it canonically and stores that.
 */
public final class HudConfigService {

    public static final String PERMISSION = "flowcity.hud.edit";

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final ClientConnectionService connections;
    private final Messages messages;
    private final HudRepository repo = new HudRepository();
    private final IntSupplier saveCooldownSeconds;
    private final Map<UUID, Long> lastSave = new ConcurrentHashMap<>();

    public HudConfigService(JavaPlugin plugin, DatabaseManager db, ClientConnectionService connections,
                            Messages messages, IntSupplier saveCooldownSeconds) {
        this.plugin = plugin;
        this.db = db;
        this.connections = connections;
        this.messages = messages;
        this.saveCooldownSeconds = saveCooldownSeconds;
    }

    public void register() {
        connections.registerHandler(PacketIds.SB_HUD_SAVE, (p, reader) -> onSave(p, reader));
    }

    /** Sends the player's saved presets (empty list = the client uses the FlowCity default layout). */
    public void sendTo(Player player) {
        UUID uuid = player.getUniqueId();
        Tasks.thenSync(plugin, db.query(c -> repo.load(c, uuid)), loaded -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                connections.send(online, new HudConfigPacket(loaded.activeSlot, loaded.presets).encode());
            }
        });
    }

    public void forget(UUID uuid) {
        lastSave.remove(uuid);
    }

    private void onSave(Player player, FlowPacketReader reader) throws ProtocolException {
        HudSavePacket packet = HudSavePacket.decode(reader);
        if (!player.hasPermission(PERMISSION)) {
            messages.send(player, "hud.no-permission");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSave.get(player.getUniqueId());
        if (previous != null && now - previous < saveCooldownSeconds.getAsInt() * 1000L) {
            messages.send(player, "hud.cooldown");
            return;
        }
        if (packet.slot < 1 || packet.slot > HudConfigPacket.MAX_SLOTS) {
            messages.send(player, "hud.invalid");
            return;
        }
        final String canonical;
        try {
            canonical = HudLayout.parse(packet.layout).encode();
        } catch (IllegalArgumentException e) {
            messages.send(player, "hud.invalid");
            return;
        }
        lastSave.put(player.getUniqueId(), now);
        final String name = cleanName(packet.name, packet.slot);
        final UUID uuid = player.getUniqueId();
        final int slot = packet.slot;
        Tasks.thenSync(plugin, db.transaction(c -> {
            repo.saveAndActivate(c, uuid, slot, name, canonical, now);
            return repo.load(c, uuid);
        }), loaded -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                connections.send(online, new HudConfigPacket(loaded.activeSlot, loaded.presets).encode());
            }
        });
    }

    /** Printable ASCII letters/digits/space/-/_ only, at most 16 characters. */
    static String cleanName(String raw, int slot) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 16; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == ' ' || ch == '-' || ch == '_') {
                sb.append(ch);
            }
        }
        String name = sb.toString().trim();
        return name.isEmpty() ? "Preset " + slot : name;
    }
}
