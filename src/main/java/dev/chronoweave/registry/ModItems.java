package dev.chronoweave.registry;

import dev.chronoweave.ChronoWeaveMod;
import dev.chronoweave.item.ChronoglassItem;
import dev.chronoweave.item.ChrononBootsItem;
import dev.chronoweave.item.EntropyManipulatorItem;
import dev.chronoweave.item.RawChrononShardItem;
import dev.chronoweave.item.RefinedChrononCrystalItem;
import dev.chronoweave.item.RiftbladeItem;
import dev.chronoweave.item.SpatialCompassItem;
import dev.chronoweave.item.StasisPrismItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
    public static final Item RAW_CHRONON_SHARD = register("raw_chronon_shard",
            new RawChrononShardItem(new Item.Settings().rarity(Rarity.UNCOMMON)));

    public static final Item REFINED_CHRONON_CRYSTAL = register("refined_chronon_crystal",
            new RefinedChrononCrystalItem(new Item.Settings().rarity(Rarity.RARE)));

    public static final Item CHRONOGLASS = register("chronoglass",
            new ChronoglassItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    public static final Item RIFTBLADE = register("riftblade",
            new RiftbladeItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    public static final Item STASIS_PRISM = register("stasis_prism",
            new StasisPrismItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE).fireproof()));

    public static final Item WORMHOLE_ASTROLABE = register("wormhole_astrolabe",
            new SpatialCompassItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    public static final Item ENTROPY_MANIPULATOR = register("entropy_manipulator",
            new EntropyManipulatorItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE).fireproof()));

    public static final Item CHRONO_STRIDER_BOOTS = register("chrono_strider_boots",
            new ChrononBootsItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(ChronoWeaveMod.MOD_ID, name), item);
    }

    public static void init() {
        // Static field initialization registers all items
    }
}
