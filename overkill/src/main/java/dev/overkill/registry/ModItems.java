package dev.overkill.registry;

import dev.overkill.OverkillMod;
import dev.overkill.item.ChronosHourglassItem;
import dev.overkill.item.EarthDevourerItem;
import dev.overkill.item.GravityGauntletItem;
import dev.overkill.item.OrbitalDesignatorItem;
import dev.overkill.item.OverkillCoreItem;
import dev.overkill.item.RingOfAbsoluteItem;
import dev.overkill.item.SingularityItem;
import dev.overkill.item.VoidReaverItem;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item OVERKILL_CORE = register("overkill_core",
			new OverkillCoreItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
	public static final Item VOID_REAVER = register("void_reaver", new VoidReaverItem(artifact()));
	public static final Item EARTH_DEVOURER = register("earth_devourer", new EarthDevourerItem(artifact()
			.component(ModComponents.DEVOURER_MODE, 0)
			.component(ModComponents.AUTO_SMELT, false)));
	public static final Item ORBITAL_DESIGNATOR = register("orbital_designator",
			new OrbitalDesignatorItem(artifact().component(ModComponents.SURGICAL, false)));
	public static final Item CHRONOS_HOURGLASS = register("chronos_hourglass", new ChronosHourglassItem(artifact()));
	public static final Item GRAVITY_GAUNTLET = register("gravity_gauntlet", new GravityGauntletItem(artifact()));
	public static final Item RING_OF_ABSOLUTE = register("ring_of_absolute",
			new RingOfAbsoluteItem(artifact().component(ModComponents.MAGNET, true)));
	public static final Item SINGULARITY = register("singularity",
			new SingularityItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

	public static final List<Item> CREATIVE_ORDER = List.of(OVERKILL_CORE, VOID_REAVER, EARTH_DEVOURER, ORBITAL_DESIGNATOR,
			CHRONOS_HOURGLASS, GRAVITY_GAUNTLET, RING_OF_ABSOLUTE, SINGULARITY);

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, OverkillMod.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(OVERKILL_CORE))
					.title(Component.translatable("itemGroup.overkill"))
					.displayItems((params, output) -> CREATIVE_ORDER.forEach(output::accept))
					.build());

	private ModItems() {
	}

	private static Item.Properties artifact() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
	}

	private static <T extends Item> T register(String name, T item) {
		return Registry.register(BuiltInRegistries.ITEM, OverkillMod.id(name), item);
	}

	public static void init() {
	}
}
