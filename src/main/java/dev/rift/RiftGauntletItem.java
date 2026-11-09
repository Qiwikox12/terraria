package dev.rift;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

public final class RiftGauntletItem extends Item {
    public RiftGauntletItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.consume(stack);
        if (!(user instanceof ServerPlayerEntity player) || !player.isAlive() || player.isSpectator()) {
            return TypedActionResult.fail(stack);
        }
        if (player.isSneaking()) {
            RiftController.cycleMode(player);
            player.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
        if (!RiftController.cast(player)) return TypedActionResult.fail(stack);
        if (RiftController.power(player).mode() == AbilityMode.VECTOR) player.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player) RiftController.releaseVector(player);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 72_000; }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    @Override
    public boolean hasGlint(ItemStack stack) { return mode(stack) == AbilityMode.SINGULARITY; }

    public static AbilityMode mode(ItemStack stack) {
        return AbilityMode.byId(stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getInt("rift_mode"));
    }

    static void setMode(ItemStack stack, AbilityMode mode) {
        if (mode(stack) == mode) return;
        NbtCompound data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        data.putInt("rift_mode", mode.ordinal());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.rift.tagline").formatted(Formatting.DARK_AQUA));
        tooltip.add(Text.empty());
        for (AbilityMode mode : AbilityMode.values()) {
            tooltip.add(Text.translatable("tooltip.rift." + mode.name().toLowerCase(java.util.Locale.ROOT), mode.cost())
                    .formatted(mode == mode(stack) ? Formatting.AQUA : Formatting.GRAY));
        }
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.rift.controls").formatted(Formatting.WHITE));
        tooltip.add(Text.translatable("tooltip.rift.safety").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.rift.guide").formatted(Formatting.DARK_GRAY));
    }
}
