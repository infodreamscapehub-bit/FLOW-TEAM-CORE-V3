package com.flowcity.forge.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

/** FlowCity keybinds; players can rebind them in Options > Controls > FlowCity Roleplay. */
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.flowcity";

    public static final KeyBinding SHOW_IDS = new KeyBinding("key.flowcity.show_ids",
            KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyBinding ID_SEARCH = new KeyBinding("key.flowcity.id_search",
            KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY);
    public static final KeyBinding PROFILE = new KeyBinding("key.flowcity.profile",
            KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY);

    private KeyBindings() {
    }

    public static void register() {
        ClientRegistry.registerKeyBinding(SHOW_IDS);
        ClientRegistry.registerKeyBinding(ID_SEARCH);
        ClientRegistry.registerKeyBinding(PROFILE);
    }
}
