package dev.chronoweave.registry;

import dev.chronoweave.ChronoWeaveMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class ModWorldGen {
    public static final RegistryKey<PlacedFeature> CHRONON_ORE_PLACED_KEY = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            Identifier.of(ChronoWeaveMod.MOD_ID, "chronon_ore_placed")
    );

    private ModWorldGen() {
    }

    public static void init() {
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                CHRONON_ORE_PLACED_KEY
        );
    }
}
