package dev.chronoweave.registry;

import dev.chronoweave.ChronoWeaveMod;
import dev.chronoweave.block.ChronoAnchorBlock;
import dev.chronoweave.block.ChrononOreBlock;
import dev.chronoweave.block.ChrononSingularityCoreBlock;
import dev.chronoweave.block.PhaseGlassBlock;
import dev.chronoweave.block.ResonantChrononBlock;
import dev.chronoweave.block.TemporalAcceleratorBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final Block CHRONON_ORE = registerBlock("chronon_ore",
            new ChrononOreBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.STONE_GRAY)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresTool()
                    .strength(3.0f, 3.0f)
                    .luminance(state -> 5)
                    .sounds(BlockSoundGroup.STONE)));

    public static final Block DEEPSLATE_CHRONON_ORE = registerBlock("deepslate_chronon_ore",
            new ChrononOreBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DEEPSLATE_GRAY)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresTool()
                    .strength(4.5f, 4.5f)
                    .luminance(state -> 7)
                    .sounds(BlockSoundGroup.DEEPSLATE)));

    public static final Block RESONANT_CHRONON_BLOCK = registerBlock("resonant_chronon_block",
            new ResonantChrononBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DIAMOND_BLUE)
                    .instrument(NoteBlockInstrument.CHIME)
                    .requiresTool()
                    .strength(5.0f, 8.0f)
                    .luminance(state -> 12)
                    .sounds(BlockSoundGroup.AMETHYST_BLOCK)));

    public static final Block PHASE_GLASS = registerBlock("phase_glass",
            new PhaseGlassBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.CYAN)
                    .instrument(NoteBlockInstrument.HAT)
                    .strength(2.5f, 1200.0f)
                    .nonOpaque()
                    .luminance(state -> 6)
                    .sounds(BlockSoundGroup.GLASS)));

    public static final Block CHRONON_SINGULARITY_CORE = registerBlock("chronon_singularity_core",
            new ChrononSingularityCoreBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.PURPLE)
                    .instrument(NoteBlockInstrument.CHIME)
                    .requiresTool()
                    .strength(6.0f, 1200.0f)
                    .luminance(state -> 15)
                    .sounds(BlockSoundGroup.LODESTONE)));

    public static final Block TEMPORAL_ACCELERATOR = registerBlock("temporal_accelerator",
            new TemporalAcceleratorBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.GOLD)
                    .instrument(NoteBlockInstrument.BELL)
                    .requiresTool()
                    .strength(4.5f, 12.0f)
                    .luminance(state -> 11)
                    .sounds(BlockSoundGroup.COPPER)));

    public static final Block CHRONO_ANCHOR = registerBlock("chrono_anchor",
            new ChronoAnchorBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLACK)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresTool()
                    .strength(8.0f, 1200.0f)
                    .luminance(state -> 13)
                    .sounds(BlockSoundGroup.NETHERITE)));

    private ModBlocks() {
    }

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(ChronoWeaveMod.MOD_ID, name);
        Block registered = Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(registered, new Item.Settings()));
        return registered;
    }

    public static void init() {
        // Static field initialization registers all blocks and block items
    }
}
