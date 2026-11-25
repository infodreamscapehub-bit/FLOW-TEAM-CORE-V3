package com.flowcity.forge.bridge;

import com.flowcity.forge.FlowCityForge;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Protocol spike, option A: can the FlowCityCore Bukkit plugin on Arclight see and call
 * classes from this Forge mod? FlowCityCore looks this class up by name via reflection
 * and calls {@link #ping()}. Only JDK types cross the boundary, so class-loader identity
 * of FlowCity classes does not matter for this test.
 */
public final class ServerBridge {

    private ServerBridge() {
    }

    public static String ping() {
        return "FlowCityForge " + FlowCityForge.version() + " dist=" + FMLEnvironment.dist;
    }
}
