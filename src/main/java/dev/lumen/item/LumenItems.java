package dev.lumen.item;

import dev.lumen.Lumen;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Rarity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class LumenItems {
    public static final ResonatorItem RESONATOR = Registry.register(Registries.ITEM, Lumen.id("resonator"),
            new ResonatorItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(RESONATOR));
    }
}
