package dev.chronoweave.registry;

import dev.chronoweave.ChronoWeaveMod;
import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.item.ChrononChargeable;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItemGroups {
    public static final ItemGroup CHRONOWEAVE_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(ChronoWeaveMod.MOD_ID, "chronoweave_group"),
            FabricItemGroup.builder()
                    .icon(() -> createChargedStack(ModItems.CHRONOGLASS))
                    .displayName(Text.translatable("itemGroup.chronoweave.chronoweave_group"))
                    .entries((context, entries) -> {
                        entries.add(ModItems.RAW_CHRONON_SHARD);
                        entries.add(ModItems.REFINED_CHRONON_CRYSTAL);

                        entries.add(createChargedStack(ModItems.CHRONOGLASS));
                        entries.add(createChargedStack(ModItems.RIFTBLADE));
                        entries.add(ModItems.STASIS_PRISM);
                        entries.add(createChargedStack(ModItems.WORMHOLE_ASTROLABE));
                        entries.add(createChargedStack(ModItems.ENTROPY_MANIPULATOR));
                        entries.add(createChargedStack(ModItems.CHRONO_STRIDER_BOOTS));

                        entries.add(ModBlocks.CHRONON_SINGULARITY_CORE);
                        entries.add(ModBlocks.TEMPORAL_ACCELERATOR);
                        entries.add(ModBlocks.CHRONO_ANCHOR);
                        entries.add(ModBlocks.RESONANT_CHRONON_BLOCK);
                        entries.add(ModBlocks.PHASE_GLASS);
                        entries.add(ModBlocks.CHRONON_ORE);
                        entries.add(ModBlocks.DEEPSLATE_CHRONON_ORE);
                    })
                    .build()
    );

    private ModItemGroups() {
    }

    public static ItemStack createChargedStack(Item item) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof ChrononChargeable chargeable) {
            int cap = chargeable.getMaxChrononCapacity();
            ChrononEnergyHelper.setCharge(stack, cap, cap);
        }
        return stack;
    }

    public static void init() {
        // Static field initialization registers the creative tab
    }
}
