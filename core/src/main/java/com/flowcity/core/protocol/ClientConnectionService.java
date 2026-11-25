package com.flowcity.core.protocol;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.Protocol;
import com.flowcity.common.protocol.ProtocolException;
import com.flowcity.common.protocol.packets.HelloAckPacket;
import com.flowcity.common.protocol.packets.HelloPacket;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server end of the flowcity:main channel (Bukkit plugin messaging).
 *
 * Phase 1 handshake: on join the server sends HELLO with a random nonce; a client running
 * FlowCityForge echoes it in HELLO_ACK. Only after that is a player's client treated as a
 * FlowCity client. Anything the client sends is size-checked, rate-limited and parsed with a
 * bounds-checked reader; unsolicited or mismatching packets are dropped.
 */
public final class ClientConnectionService implements Listener, PluginMessageListener {

    public enum State {
        /** HELLO sent, waiting for the answer. */
        PENDING,
        /** Client answered correctly: FlowCityForge with a matching protocol. */
        CONFIRMED,
        /** No answer before the timeout: vanilla client, missing mod, or channel not registered. */
        NO_MOD,
        /** Client answered with a wrong nonce or a malformed/incompatible packet. */
        REJECTED
    }

    public static final class Session {
        final long nonce;
        volatile State state = State.PENDING;
        volatile String modVersion = "";
        volatile String detail = "";
        long windowSecond;
        int packetsInWindow;

        Session(long nonce) {
            this.nonce = nonce;
        }

        public State state() {
            return state;
        }

        public String modVersion() {
            return modVersion;
        }

        public String detail() {
            return detail;
        }
    }

    private final JavaPlugin plugin;
    private final int helloDelayTicks;
    private final int helloTimeoutTicks;
    private final int maxPacketsPerSecond;
    private final boolean debug;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Integer, PacketHandler> handlers = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> readyListeners = new CopyOnWriteArrayList<>();

    public ClientConnectionService(JavaPlugin plugin, int helloDelayTicks, int helloTimeoutTicks,
                                   int maxPacketsPerSecond, boolean debug) {
        this.plugin = plugin;
        this.helloDelayTicks = Math.max(1, helloDelayTicks);
        this.helloTimeoutTicks = Math.max(20, helloTimeoutTicks);
        this.maxPacketsPerSecond = Math.max(1, maxPacketsPerSecond);
        this.debug = debug;
    }

    public void register() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Players already online (e.g. after /reload) get a handshake too.
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            scheduleHello(p);
        }
    }

    public void unregister() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin, Protocol.CHANNEL);
        messenger.unregisterIncomingPluginChannel(plugin, Protocol.CHANNEL, this);
        HandlerList.unregisterAll(this);
        sessions.clear();
    }

    public Session session(UUID uuid) {
        return sessions.get(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleHello(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void scheduleHello(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null && online.isOnline()) {
                sendHello(online);
            }
        }, helloDelayTicks);
    }

    private void sendHello(Player player) {
        UUID uuid = player.getUniqueId();
        Session session = new Session(random.nextLong());
        sessions.put(uuid, session);
        if (debug) {
            plugin.getLogger().info("[protocol] " + player.getName() + " listening channels: " + player.getListeningPluginChannels());
        }
        player.sendPluginMessage(plugin, Protocol.CHANNEL,
                new HelloPacket(session.nonce, plugin.getDescription().getVersion()).encode());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(uuid) == session && session.state == State.PENDING) {
                session.state = State.NO_MOD;
                session.detail = player.getListeningPluginChannels().contains(Protocol.CHANNEL)
                        ? "channel registered but no answer"
                        : "client did not register " + Protocol.CHANNEL;
                plugin.getLogger().info("[protocol] " + player.getName() + ": no FlowCity client (" + session.detail + ")");
            }
        }, helloTimeoutTicks);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Protocol.CHANNEL.equals(channel)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            debugDrop(player, "unsolicited packet before handshake");
            return;
        }
        if (!withinRateLimit(session)) {
            debugDrop(player, "rate limit exceeded");
            return;
        }
        try {
            FlowPacketReader reader = FlowPacketReader.open(message, Protocol.MAX_SERVERBOUND_BYTES);
            switch (reader.packetId()) {
                case PacketIds.SB_HELLO_ACK:
                    handleHelloAck(player, session, HelloAckPacket.decode(reader));
                    break;
                default: {
                    PacketHandler handler = handlers.get(reader.packetId());
                    if (handler == null) {
                        debugDrop(player, "unknown packet id " + reader.packetId());
                    } else if (session.state != State.CONFIRMED) {
                        debugDrop(player, "packet " + reader.packetId() + " before handshake completed");
                    } else {
                        handler.handle(player, reader);
                    }
                }
            }
        } catch (ProtocolException e) {
            if (session.state == State.PENDING) {
                session.state = State.REJECTED;
                session.detail = e.getMessage();
            }
            plugin.getLogger().warning("[protocol] Rejected packet from " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleHelloAck(Player player, Session session, HelloAckPacket ack) {
        if (session.state != State.PENDING) {
            debugDrop(player, "duplicate HELLO_ACK");
            return;
        }
        if (ack.nonce != session.nonce) {
            session.state = State.REJECTED;
            session.detail = "nonce mismatch";
            plugin.getLogger().warning("[protocol] " + player.getName() + " answered with a wrong nonce");
            return;
        }
        session.modVersion = ack.modVersion;
        session.state = State.CONFIRMED;
        session.detail = "ok";
        plugin.getLogger().info("[protocol] " + player.getName() + ": FlowCity client confirmed (FlowCityForge "
                + ack.modVersion + ", protocol " + Protocol.VERSION + ")");
        for (Consumer<Player> l : readyListeners) {
            try {
                l.accept(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Client-ready listener failed", e);
            }
        }
    }

    /** Handles one serverbound packet type; only called for CONFIRMED clients, on the main thread. */
    @FunctionalInterface
    public interface PacketHandler {
        void handle(Player player, FlowPacketReader reader) throws ProtocolException;
    }

    public void registerHandler(int packetId, PacketHandler handler) {
        if (packetId == PacketIds.SB_HELLO_ACK || handlers.putIfAbsent(packetId, handler) != null) {
            throw new IllegalStateException("Handler already registered for packet " + packetId);
        }
    }

    /** Runs when a player's client completes the handshake (main thread). */
    public void onClientReady(Consumer<Player> listener) {
        readyListeners.add(listener);
    }

    public boolean isConfirmed(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s != null && s.state == State.CONFIRMED;
    }

    /** Sends a FlowCity payload; silently skipped for players without a confirmed FlowCity client. */
    public boolean send(Player player, byte[] payload) {
        if (!isConfirmed(player)) {
            return false;
        }
        if (payload.length > Protocol.MAX_CLIENTBOUND_BYTES) {
            throw new IllegalArgumentException("Payload too large: " + payload.length);
        }
        player.sendPluginMessage(plugin, Protocol.CHANNEL, payload);
        return true;
    }

    /** Fixed one-second window per player. Called only from the main thread. */
    private boolean withinRateLimit(Session session) {
        long second = System.currentTimeMillis() / 1000L;
        if (session.windowSecond != second) {
            session.windowSecond = second;
            session.packetsInWindow = 0;
        }
        return ++session.packetsInWindow <= maxPacketsPerSecond;
    }

    private void debugDrop(Player player, String why) {
        if (debug) {
            plugin.getLogger().info("[protocol] Dropped packet from " + player.getName() + ": " + why);
        }
    }
}
