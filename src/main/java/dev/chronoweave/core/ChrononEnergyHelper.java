package dev.chronoweave.core;

import dev.chronoweave.registry.ModDataComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

public final class ChrononEnergyHelper {
    private ChrononEnergyHelper() {
    }

    public static int getCharge(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(ModDataComponents.CHRONON_CHARGE, 0));
    }

    public static void setCharge(ItemStack stack, int amount, int maxCapacity) {
        int clamped = MathHelper.clamp(amount, 0, maxCapacity);
        stack.set(ModDataComponents.CHRONON_CHARGE, clamped);
    }

    public static int addCharge(ItemStack stack, int delta, int maxCapacity) {
        int current = getCharge(stack);
        int updated = MathHelper.clamp(current + delta, 0, maxCapacity);
        stack.set(ModDataComponents.CHRONON_CHARGE, updated);
        return updated - current;
    }

    public static boolean tryConsume(ItemStack stack, PlayerEntity player, int cost) {
        if (player != null && player.isCreative()) {
            return true;
        }
        int current = getCharge(stack);
        if (current < cost) {
            return false;
        }
        stack.set(ModDataComponents.CHRONON_CHARGE, current - cost);
        return true;
    }

    public static int getBarStep(ItemStack stack, int maxCapacity) {
        if (maxCapacity <= 0) {
            return 0;
        }
        float ratio = (float) getCharge(stack) / (float) maxCapacity;
        return MathHelper.clamp(Math.round(ratio * 13.0f), 0, 13);
    }

    public static int getBarColor(ItemStack stack, int maxCapacity) {
        if (maxCapacity <= 0) {
            return 0x00F5D4;
        }
        float ratio = MathHelper.clamp((float) getCharge(stack) / (float) maxCapacity, 0.0f, 1.0f);
        // Smoothly interpolate from violet-magenta (low) to electric cyan-gold (full)
        int r = (int) MathHelper.lerp(ratio, 180.0f, 0.0f);
        int g = (int) MathHelper.lerp(ratio, 60.0f, 245.0f);
        int b = (int) MathHelper.lerp(ratio, 255.0f, 212.0f);
        return (r << 16) | (g << 8) | b;
    }

    public static MutableText buildChargeBarTooltip(int current, int maxCapacity) {
        int segments = 10;
        int filled = maxCapacity > 0
                ? MathHelper.clamp(Math.round(((float) current / maxCapacity) * segments), 0, segments)
                : 0;
        StringBuilder filledPart = new StringBuilder();
        StringBuilder emptyPart = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            if (i < filled) {
                filledPart.append("█");
            } else {
                emptyPart.append("░");
            }
        }
        return Text.literal("⟦").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(filledPart.toString()).formatted(Formatting.AQUA))
                .append(Text.literal(emptyPart.toString()).formatted(Formatting.DARK_PURPLE))
                .append(Text.literal("⟧ ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(current + " / " + maxCapacity + " Chr").formatted(Formatting.GOLD));
    }
}
