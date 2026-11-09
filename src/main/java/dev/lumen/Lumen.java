package dev.lumen;

import dev.lumen.config.LumenConfig;
import dev.lumen.item.LumenComponents;
import dev.lumen.item.LumenItems;
import dev.lumen.network.LumenNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lumen implements ModInitializer {
    public static final String MOD_ID = "lumen";
    public static final Logger LOGGER = LoggerFactory.getLogger("LUMEN");
    private static volatile LumenConfig config = new LumenConfig();

    @Override
    public void onInitialize() {
        config = LumenConfig.load();
        LumenComponents.init();
        LumenItems.init();
        LumenStats.init();
        LumenNetworking.init();
        LOGGER.info("LUMEN is tuned and ready");
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static LumenConfig config() {
        return config;
    }
}
