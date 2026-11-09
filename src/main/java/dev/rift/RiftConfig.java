package dev.rift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RiftConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public float damageMultiplier = 1;
    public float energyRegenPerSecond = 10;
    public int maxHeldMobs = 6;
    public boolean affectPassiveMobs = false;

    public static RiftConfig load(Path path) {
        RiftConfig config = new RiftConfig();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    RiftConfig loaded = GSON.fromJson(reader, RiftConfig.class);
                    if (loaded != null) config = loaded;
                }
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(config) + "\n");
            }
        } catch (IOException | RuntimeException exception) {
            LoggerFactory.getLogger("rift").warn("Cannot load {}; using safe defaults", path, exception);
        }
        config.damageMultiplier = finiteClamp(config.damageMultiplier, 0.1f, 10, 1);
        config.energyRegenPerSecond = finiteClamp(config.energyRegenPerSecond, 0, 100, 10);
        config.maxHeldMobs = Math.clamp(config.maxHeldMobs, 1, 8);
        return config;
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
