package dev.rift;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RiftConfigTest {
    @TempDir Path directory;

    @Test
    void createsDefaultsAndIsIdempotent() throws Exception {
        Path path = directory.resolve("rift.json");
        RiftConfig defaults = RiftConfig.load(path);
        String contents = Files.readString(path);
        RiftConfig loaded = RiftConfig.load(path);
        assertEquals(1, defaults.damageMultiplier);
        assertEquals(10, loaded.energyRegenPerSecond);
        assertEquals(6, loaded.maxHeldMobs);
        assertFalse(loaded.affectPassiveMobs);
        assertEquals(contents, Files.readString(path));
    }

    @Test
    void clampsUnsafeLimits() throws Exception {
        Path path = directory.resolve("rift.json");
        Files.writeString(path, "{\"damageMultiplier\":999,\"energyRegenPerSecond\":-4,\"maxHeldMobs\":900}");
        RiftConfig config = RiftConfig.load(path);
        assertEquals(10, config.damageMultiplier);
        assertEquals(0, config.energyRegenPerSecond);
        assertEquals(8, config.maxHeldMobs);
    }

    @Test
    void brokenJsonFallsBackWithoutOverwritingUserFile() throws Exception {
        Path path = directory.resolve("rift.json");
        Files.writeString(path, "{ this is not json }");
        RiftConfig config = RiftConfig.load(path);
        assertEquals(1, config.damageMultiplier);
        assertEquals("{ this is not json }", Files.readString(path));
    }

    @Test
    void missingFieldsKeepDefaults() throws Exception {
        Path path = directory.resolve("rift.json");
        Files.writeString(path, "{\"affectPassiveMobs\":true}");
        RiftConfig config = RiftConfig.load(path);
        assertTrue(config.affectPassiveMobs);
        assertEquals(10, config.energyRegenPerSecond);
        assertEquals(6, config.maxHeldMobs);
    }

    @Test
    void nonFiniteNumbersCannotPoisonCombatState() throws Exception {
        Path path = directory.resolve("rift.json");
        Files.writeString(path, "{\"damageMultiplier\":\"NaN\",\"energyRegenPerSecond\":\"Infinity\"}");
        RiftConfig config = RiftConfig.load(path);
        assertEquals(1, config.damageMultiplier);
        assertEquals(10, config.energyRegenPerSecond);
    }
}
