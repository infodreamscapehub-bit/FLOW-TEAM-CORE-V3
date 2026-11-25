package com.flowcity.forge.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/flowcity-client.toml - per-player visual settings. Keybinds live in Options > Controls. */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue ID_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue ID_FADE_MILLIS;
    public static final ForgeConfigSpec.DoubleValue ID_SCALE;
    public static final ForgeConfigSpec.BooleanValue HIDE_VANILLA_NAMETAGS;
    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> THEME_ACCENT;
    public static final ForgeConfigSpec.ConfigValue<String> THEME_ACCENT_2;
    public static final ForgeConfigSpec.ConfigValue<String> THEME_PANEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ids");
        ID_MAX_DISTANCE = b.comment("Max distance (blocks) for drawing IDs. The server may send fewer.")
                .defineInRange("maxDistance", 16.0, 2.0, 64.0);
        ID_FADE_MILLIS = b.comment("Fade in/out time of IDs when pressing or releasing the key.")
                .defineInRange("fadeMillis", 150, 0, 2000);
        ID_SCALE = b.comment("Size of the ID label.")
                .defineInRange("scale", 1.0, 0.5, 3.0);
        HIDE_VANILLA_NAMETAGS = b.comment("Hide vanilla player name tags (Minecraft usernames).")
                .define("hideVanillaNametags", true);
        b.pop();
        b.push("hud");
        HUD_ENABLED = b.comment("Draw the FlowCity HUD. Layout and colours are changed in the HUD editor.")
                .define("enabled", true);
        b.pop();
        b.push("theme");
        THEME_ACCENT = b.comment("Main accent colour of all FlowCity interfaces (#RRGGBB).")
                .define("accent", "#FF8C00");
        THEME_ACCENT_2 = b.comment("Second accent colour (#RRGGBB), used for IDs.")
                .define("accent2", "#FFC400");
        THEME_PANEL = b.comment("Panel background colour (#RRGGBB).")
                .define("panel", "#121212");
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
