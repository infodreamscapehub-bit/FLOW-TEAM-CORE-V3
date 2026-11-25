package com.flowcity.forge.client;

import com.flowcity.common.protocol.packets.ProfileRequestPacket;
import com.flowcity.forge.client.screen.IdSearchScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client-only wiring. Only referenced through DistExecutor, so servers never load it. */
public final class ClientEvents {

    private ClientEvents() {
    }

    public static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientEvents::onClientSetup);

        IEventBus forge = MinecraftForge.EVENT_BUS;
        forge.addListener(ClientEvents::onClientTick);
        forge.addListener(ClientEvents::onNameplate);
        forge.addListener(ClientEvents::onRenderPlayer);
        forge.addListener(ClientEvents::onLoggedOut);

        ClientNetwork.init();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(KeyBindings::register);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.player != null && mc.level != null;
        IdRenderer.setVisible(inWorld && mc.screen == null
                && (KeyBindings.SHOW_IDS.isDown() || ClientState.alwaysShowIds()));
        if (!inWorld) {
            return;
        }
        while (KeyBindings.ID_SEARCH.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new IdSearchScreen());
            }
        }
        while (KeyBindings.PROFILE.consumeClick()) {
            if (mc.screen == null) {
                ClientNetwork.send(new ProfileRequestPacket(0).encode());
            }
        }
    }

    private static void onNameplate(RenderNameplateEvent event) {
        IdRenderer.onNameplate(event);
    }

    private static void onRenderPlayer(RenderPlayerEvent.Post event) {
        IdRenderer.onRenderPlayer(event);
    }

    private static void onLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ClientState.clear();
    }
}
