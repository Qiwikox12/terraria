package dev.chronoweave.core;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bidirectional block entropy chains for forward aging and reverse restoration.
 */
public final class EntropyTransformationRegistry {
    private static final Map<Block, Block> FORWARD_MAP = new LinkedHashMap<>();
    private static final Map<Block, Block> REVERSE_MAP = new LinkedHashMap<>();

    static {
        // Lithic weathering and erosion chain
        registerChain(Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL, Blocks.SAND, Blocks.GLASS);
        registerChain(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS);
        registerChain(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);

        // Copper oxidation chain
        registerChain(Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER);
        registerChain(Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER);

        // Soil & biosphere succession
        registerChain(Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);

        // Thermal & volcanic entropy
        registerChain(Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);

        // High-pressure carbon crystallization & raw ore refinement
        FORWARD_MAP.put(Blocks.COAL_BLOCK, Blocks.DIAMOND_ORE);
        REVERSE_MAP.put(Blocks.DIAMOND_ORE, Blocks.COAL_BLOCK);

        FORWARD_MAP.put(Blocks.RAW_IRON_BLOCK, Blocks.IRON_BLOCK);
        FORWARD_MAP.put(Blocks.RAW_GOLD_BLOCK, Blocks.GOLD_BLOCK);
        FORWARD_MAP.put(Blocks.RAW_COPPER_BLOCK, Blocks.COPPER_BLOCK);

        // Anvil entropy & temporal restoration
        registerChain(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL);
    }

    private EntropyTransformationRegistry() {
    }

    private static void registerChain(Block... chain) {
        for (int i = 0; i < chain.length - 1; i++) {
            FORWARD_MAP.put(chain[i], chain[i + 1]);
            REVERSE_MAP.put(chain[i + 1], chain[i]);
        }
    }

    public static Optional<Block> getForwardResult(Block input) {
        return Optional.ofNullable(FORWARD_MAP.get(input));
    }

    public static Optional<Block> getReverseResult(Block input) {
        return Optional.ofNullable(REVERSE_MAP.get(input));
    }

    public static int getChrononCost(Block input, boolean reverse) {
        if (input == Blocks.COAL_BLOCK && !reverse) {
            return 250; // Extreme temporal compression to crystallize diamond ore
        }
        if ((input == Blocks.DAMAGED_ANVIL || input == Blocks.CHIPPED_ANVIL) && reverse) {
            return 80; // Restoring forged iron structure
        }
        return 20;
    }
}
