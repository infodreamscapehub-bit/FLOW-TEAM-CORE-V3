package com.flowcity.forge.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/flowcity-client.toml - per-player visual settings. Keybinds live in Options > Controls. */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue ID_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue ID_FADE_MILLIS;
    public static final ForgeConfigSpec.DoubleValue ID_SCALE;
    public static final ForgeConfigSpec.BooleanValue HIDE_VANILLA_NAMETAGS;

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
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
