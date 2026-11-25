package com.flowcity.forge.client;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.Protocol;
import com.flowcity.common.protocol.ProtocolException;
import com.flowcity.common.protocol.packets.HelloAckPacket;
import com.flowcity.common.protocol.packets.HelloPacket;
import com.flowcity.common.protocol.packets.HudConfigPacket;
import com.flowcity.common.protocol.packets.IdVisibilityPacket;
import com.flowcity.common.protocol.packets.MoneyStatePacket;
import com.flowcity.common.protocol.packets.NearbyIdsPacket;
import com.flowcity.common.protocol.packets.PlayerStatePacket;
import com.flowcity.common.protocol.packets.ProfilePacket;
import com.flowcity.forge.client.screen.ProfileScreen;
import com.flowcity.forge.FlowCityForge;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.event.EventNetworkChannel;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Client end of the flowcity:main plugin-messaging channel.
 *
 * Registered as a raw {@link EventNetworkChannel} that accepts any (or no) remote version, so:
 *  - connecting to a server without FlowCityCore does not fail the Forge handshake;
 *  - the Bukkit plugin on Arclight can talk to it with plain plugin messages.
 * Protocol versioning is done inside our own packet header instead (see {@link Protocol}).
 */
public final class ClientNetwork {

    public static final ResourceLocation CHANNEL =
            new ResourceLocation(Protocol.CHANNEL_NAMESPACE, Protocol.CHANNEL_PATH);

    private static EventNetworkChannel channel;

    private ClientNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.ChannelBuilder.named(CHANNEL)
                .networkProtocolVersion(() -> String.valueOf(Protocol.VERSION))
                .clientAcceptedVersions(remote -> true)
                .serverAcceptedVersions(remote -> true)
                .eventNetworkChannel();
        channel.addListener(ClientNetwork::onNetworkEvent);
        FlowCityForge.LOGGER.info("Registered client channel {}", CHANNEL);
    }

    private static void onNetworkEvent(NetworkEvent event) {
        NetworkEvent.Context ctx = event.getSource() == null ? null : event.getSource().get();
        if (ctx == null || ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT || event.getPayload() == null) {
            return;
        }
        PacketBuffer buf = event.getPayload();
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        ctx.setPacketHandled(true);
        ctx.enqueueWork(() -> handle(data));
    }

    private static void handle(byte[] data) {
        try {
            FlowPacketReader reader = FlowPacketReader.open(data, Protocol.MAX_CLIENTBOUND_BYTES);
            switch (reader.packetId()) {
                case PacketIds.CB_HELLO: {
                    HelloPacket hello = HelloPacket.decode(reader);
                    FlowCityForge.LOGGER.info("FlowCity server hello (server {}), answering", hello.serverVersion);
                    send(new HelloAckPacket(hello.nonce, FlowCityForge.version()).encode());
                    break;
                }
                case PacketIds.CB_NEARBY_IDS:
                    ClientState.setNearby(NearbyIdsPacket.decode(reader));
                    break;
                case PacketIds.CB_ID_VISIBILITY:
                    ClientState.setAlwaysShowIds(IdVisibilityPacket.decode(reader).alwaysShow);
                    break;
                case PacketIds.CB_PLAYER_STATE:
                    ClientState.setSelf(PlayerStatePacket.decode(reader));
                    break;
                case PacketIds.CB_MONEY_STATE:
                    ClientState.setMoney(MoneyStatePacket.decode(reader));
                    break;
                case PacketIds.CB_HUD_CONFIG:
                    ClientState.setHudConfig(HudConfigPacket.decode(reader));
                    break;
                case PacketIds.CB_PROFILE: {
                    ProfilePacket profile = ProfilePacket.decode(reader);
                    Minecraft.getInstance().setScreen(new ProfileScreen(profile));
                    break;
                }
                default:
                    FlowCityForge.LOGGER.warn("Unknown FlowCity packet id {}", reader.packetId());
            }
        } catch (ProtocolException e) {
            FlowCityForge.LOGGER.warn("Rejected FlowCity packet: {}", e.getMessage());
        }
    }

    /** Sends a raw FlowCity payload to the server on flowcity:main. */
    public static void send(byte[] bytes) {
        ClientPlayNetHandler connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
        IPacket<?> packet = NetworkDirection.PLAY_TO_SERVER.<IPacket<?>>buildPacket(Pair.of(buf, 0), CHANNEL).getThis();
        connection.send(packet);
    }
}
