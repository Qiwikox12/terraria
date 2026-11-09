package dev.eventhorizon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.eventhorizon.EventHorizon;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GravityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean allowPlayerTargets = false;
    public boolean affectPassiveMobs = false;
    public boolean showParticles = true;
    public int regenerationIntervalTicks = 4;
    public int maxAffectedEntities = 48;
    public double damageMultiplier = 1.0;

    public static GravityConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("eventhorizon.json");
        GravityConfig defaults = new GravityConfig();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaults) + "\n");
                return defaults;
            }
            GravityConfig config = GSON.fromJson(Files.readString(path), GravityConfig.class);
            if (config == null) throw new IllegalArgumentException("Expected a JSON object");
            config.regenerationIntervalTicks = Math.clamp(config.regenerationIntervalTicks, 1, 200);
            config.maxAffectedEntities = Math.clamp(config.maxAffectedEntities, 1, 128);
            config.damageMultiplier = Double.isFinite(config.damageMultiplier)
                ? Math.clamp(config.damageMultiplier, 0.1, 10.0) : 1.0;
            return config;
        } catch (IOException | RuntimeException exception) {
            EventHorizon.LOGGER.warn("Cannot read eventhorizon.json; using safe defaults. Original file preserved.", exception);
            return defaults;
        }
    }
}
