package com.flowcity.forge;

import com.flowcity.forge.client.ClientNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FlowCityForge.MOD_ID)
public final class FlowCityForge {

    public static final String MOD_ID = "flowcity";
    public static final Logger LOGGER = LogManager.getLogger("FlowCityForge");

    public FlowCityForge() {
        LOGGER.info("FlowCityForge {} loading", version());
        // The flowcity:main channel is registered on the CLIENT only.
        // On the Arclight server the same channel name is owned by the FlowCityCore Bukkit plugin
        // (plugin messaging). If the server half of this mod registered it too, Forge would consume
        // the payloads before Bukkit sees them.
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientNetwork::init);
    }

    public static String version() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
