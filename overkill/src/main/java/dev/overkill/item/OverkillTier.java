package dev.overkill.item;

import dev.overkill.registry.ModItems;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public enum OverkillTier implements Tier {
	INSTANCE;

	@Override
	public int getUses() {
		// Irrelevant in practice: every tiered artifact is also Unbreakable.
		return 9999;
	}

	@Override
	public float getSpeed() {
		return 2000.0F;
	}

	@Override
	public float getAttackDamageBonus() {
		return 0.0F;
	}

	@Override
	public TagKey<Block> getIncorrectBlocksForDrops() {
		return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
	}

	@Override
	public int getEnchantmentValue() {
		return 30;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.of(ModItems.OVERKILL_CORE);
	}
}
