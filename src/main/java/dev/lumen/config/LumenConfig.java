package dev.lumen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lumen.Lumen;
import dev.lumen.util.ResonanceMath;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LumenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int range = 14;
    public int deepRange = 22;
    public int maxCharge = 100;
    public int scanCost = 30;
    public int deepScanCost = 60;
    public int rechargeTicks = 5;
    public int highlightTicks = 180;
    public int maxHighlights = 96;
    public int cooldownTicks = 80;
    public int deepCooldownTicks = 200;

    public static LumenConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("lumen.json");
        LumenConfig loaded = new LumenConfig();
        if (Files.isRegularFile(path)) {
            try {
                LumenConfig parsed = GSON.fromJson(Files.readString(path), LumenConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception exception) {
                Lumen.LOGGER.error("Could not read {}; using safe defaults", path, exception);
            }
        }
        loaded.sanitize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(loaded) + System.lineSeparator());
        } catch (IOException exception) {
            Lumen.LOGGER.error("Could not save {}", path, exception);
        }
        return loaded;
    }

    private void sanitize() {
        range = ResonanceMath.clamp(range, 4, 24);
        deepRange = ResonanceMath.clamp(deepRange, range, 32);
        maxCharge = ResonanceMath.clamp(maxCharge, 20, 500);
        scanCost = ResonanceMath.clamp(scanCost, 1, maxCharge);
        deepScanCost = ResonanceMath.clamp(deepScanCost, scanCost, maxCharge);
        rechargeTicks = ResonanceMath.clamp(rechargeTicks, 1, 200);
        highlightTicks = ResonanceMath.clamp(highlightTicks, 40, 600);
        maxHighlights = ResonanceMath.clamp(maxHighlights, 16, 192);
        cooldownTicks = ResonanceMath.clamp(cooldownTicks, 0, 1200);
        deepCooldownTicks = ResonanceMath.clamp(deepCooldownTicks, cooldownTicks, 2400);
    }
}
