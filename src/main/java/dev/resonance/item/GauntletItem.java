package dev.resonance.item;

import dev.resonance.ability.AbilityRules;
import dev.resonance.ability.Mode;
import dev.resonance.ability.ResonanceAbilities;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class GauntletItem extends Item {
    public static final int MAX_ENERGY = AbilityRules.MAX_ENERGY;

    public GauntletItem(Settings settings) {
        super(settings.maxCount(1).component(ResonanceComponents.ENERGY, MAX_ENERGY)
                .component(ResonanceComponents.MODE, 0));
    }

    public static int getEnergy(ItemStack stack) {
        return AbilityRules.clampEnergy(stack.getOrDefault(ResonanceComponents.ENERGY, MAX_ENERGY));
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ResonanceComponents.ENERGY, AbilityRules.clampEnergy(energy));
    }

    public static Mode getMode(ItemStack stack) {
        return Mode.fromIndex(stack.getOrDefault(ResonanceComponents.MODE, 0));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        if (user instanceof ServerPlayerEntity player) {
            if (user.isSneaking()) {
                ResonanceAbilities.cycleMode(player, 1);
            } else {
                ResonanceAbilities.cast(player);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return getEnergy(stack) < MAX_ENERGY;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0F * getEnergy(stack) / MAX_ENERGY);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return getMode(stack).color();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.resonance.mode", Text.translatable(getMode(stack).translationKey()))
                .withColor(getMode(stack).color()));
        tooltip.add(Text.translatable("tooltip.resonance.energy", getEnergy(stack), MAX_ENERGY)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.resonance.controls").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.resonance.guide").formatted(Formatting.DARK_GRAY));
    }
}
