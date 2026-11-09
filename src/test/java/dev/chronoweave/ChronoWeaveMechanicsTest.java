package dev.chronoweave;

import com.google.gson.JsonParser;
import dev.chronoweave.block.entity.ChrononSingularityCoreBlockEntity;
import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.core.EntropyTransformationRegistry;
import dev.chronoweave.core.ParadoxEchoManager;
import dev.chronoweave.core.TemporalHistoryManager;
import dev.chronoweave.registry.ModBlocks;
import dev.chronoweave.registry.ModDataComponents;
import dev.chronoweave.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ChronoWeaveMechanicsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        new ChronoWeaveMod().onInitialize();
    }

    @Test
    void testAllBlocksAndItemsRegistered() {
        assertEquals(ModItems.CHRONOGLASS, Registries.ITEM.get(Identifier.of("chronoweave", "chronoglass")));
        assertEquals(ModItems.RIFTBLADE, Registries.ITEM.get(Identifier.of("chronoweave", "riftblade")));
        assertEquals(ModItems.STASIS_PRISM, Registries.ITEM.get(Identifier.of("chronoweave", "stasis_prism")));
        assertEquals(ModItems.WORMHOLE_ASTROLABE, Registries.ITEM.get(Identifier.of("chronoweave", "wormhole_astrolabe")));
        assertEquals(ModItems.ENTROPY_MANIPULATOR, Registries.ITEM.get(Identifier.of("chronoweave", "entropy_manipulator")));
        assertEquals(ModItems.CHRONO_STRIDER_BOOTS, Registries.ITEM.get(Identifier.of("chronoweave", "chrono_strider_boots")));

        assertEquals(ModBlocks.CHRONON_SINGULARITY_CORE, Registries.BLOCK.get(Identifier.of("chronoweave", "chronon_singularity_core")));
        assertEquals(ModBlocks.TEMPORAL_ACCELERATOR, Registries.BLOCK.get(Identifier.of("chronoweave", "temporal_accelerator")));
        assertEquals(ModBlocks.CHRONO_ANCHOR, Registries.BLOCK.get(Identifier.of("chronoweave", "chrono_anchor")));
        assertEquals(ModBlocks.PHASE_GLASS, Registries.BLOCK.get(Identifier.of("chronoweave", "phase_glass")));
        assertNotNull(ModDataComponents.CHRONON_CHARGE);
    }

    @Test
    void testChrononEnergyChargeAndConsumption() {
        ItemStack stack = new ItemStack(ModItems.CHRONOGLASS);
        assertEquals(0, ChrononEnergyHelper.getCharge(stack));

        int added = ChrononEnergyHelper.addCharge(stack, 800, 1200);
        assertEquals(800, added);
        assertEquals(800, ChrononEnergyHelper.getCharge(stack));

        // Clamping at max capacity
        int overflowAdded = ChrononEnergyHelper.addCharge(stack, 900, 1200);
        assertEquals(400, overflowAdded);
        assertEquals(1200, ChrononEnergyHelper.getCharge(stack));
        assertEquals(13, ChrononEnergyHelper.getBarStep(stack, 1200));

        // Consumption
        assertTrue(ChrononEnergyHelper.tryConsume(stack, null, 350));
        assertEquals(850, ChrononEnergyHelper.getCharge(stack));
        assertFalse(ChrononEnergyHelper.tryConsume(stack, null, 900));
        assertEquals(850, ChrononEnergyHelper.getCharge(stack));
    }

    @Test
    void testTemporalRewindRingBufferAndPeakHealthPreservation() {
        UUID playerId = UUID.randomUUID();
        for (int i = 0; i < 30; i++) {
            float simulatedHp = (i == 15) ? 20.0f : 8.0f;
            TemporalHistoryManager.recordSnapshot(playerId, new TemporalHistoryManager.TemporalSnapshot(
                    i * 5L,
                    World.OVERWORLD,
                    i * 2.0, 64.0, 0.0,
                    0.1, 0.0, 0.0,
                    90.0f, 0.0f,
                    simulatedHp,
                    20, 5.0f
            ));
        }
        assertEquals(TemporalHistoryManager.MAX_SNAPSHOTS, TemporalHistoryManager.getSnapshotCount(playerId));

        Optional<TemporalHistoryManager.TemporalSnapshot> rewind =
                TemporalHistoryManager.popBestRewindSnapshot(playerId, World.OVERWORLD);
        assertTrue(rewind.isPresent());
        // Oldest snapshot in the 24-slot window is index 6 (x = 12.0), while peak health in window is 20.0f
        assertEquals(12.0, rewind.get().x(), 0.001);
        assertEquals(20.0f, rewind.get().health(), 0.001f);
        assertEquals(0, TemporalHistoryManager.getSnapshotCount(playerId));
    }

    @Test
    void testParadoxEchoComboScaling() {
        ParadoxEchoManager.clearAll();
        UUID target = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        ParadoxEchoManager.ParadoxMark mark = ParadoxEchoManager.recordStrike(target, attacker, 10.0f, 100L, 35);
        assertEquals(1, mark.getComboHits());
        assertEquals(4.5f, mark.computeEchoDamage(), 0.01f);

        ParadoxEchoManager.recordStrike(target, attacker, 10.0f, 110L, 35);
        assertEquals(2, mark.getComboHits());
        // 20 accumulated * 0.45 * 1.15 = 10.35
        assertEquals(10.35f, mark.computeEchoDamage(), 0.01f);
    }

    @Test
    void testEntropyTransformationChains() {
        assertEquals(Optional.of(Blocks.COBBLESTONE), EntropyTransformationRegistry.getForwardResult(Blocks.STONE));
        assertEquals(Optional.of(Blocks.DIAMOND_ORE), EntropyTransformationRegistry.getForwardResult(Blocks.COAL_BLOCK));
        assertEquals(Optional.of(Blocks.CHIPPED_ANVIL), EntropyTransformationRegistry.getReverseResult(Blocks.DAMAGED_ANVIL));
        assertEquals(Optional.of(Blocks.ANVIL), EntropyTransformationRegistry.getReverseResult(Blocks.CHIPPED_ANVIL));
        assertEquals(Optional.of(Blocks.COPPER_BLOCK), EntropyTransformationRegistry.getReverseResult(Blocks.EXPOSED_COPPER));
        assertEquals(250, EntropyTransformationRegistry.getChrononCost(Blocks.COAL_BLOCK, false));
    }

    @Test
    void testSingularityCoreResonanceMath() {
        int baseRate = ChrononSingularityCoreBlockEntity.calculateResonanceRate(0, 0, false);
        assertEquals(2, baseRate);
        int boostedRate = ChrononSingularityCoreBlockEntity.calculateResonanceRate(4, 2, true);
        // (2 + 4*2 + 2*4) * 2 = 36
        assertEquals(36, boostedRate);
    }

    @Test
    void testAllResourceJsonFilesAreValid() throws IOException {
        Path resourcesRoot = Path.of("src/main/resources");
        try (Stream<Path> paths = Files.walk(resourcesRoot)) {
            paths.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".mcmeta"))
                    .forEach(path -> {
                        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                            assertNotNull(JsonParser.parseReader(reader), "Null JSON in " + path);
                        } catch (Exception e) {
                            fail("Invalid JSON in " + path + ": " + e.getMessage());
                        }
                    });
        }
    }
}
