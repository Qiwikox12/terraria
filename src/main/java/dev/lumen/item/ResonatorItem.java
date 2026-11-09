package dev.lumen.item;

import dev.lumen.Lumen;
import dev.lumen.config.LumenConfig;
import dev.lumen.server.ResonanceScanner;
import dev.lumen.util.ResonanceMath;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

public final class ResonatorItem extends Item {
    public ResonatorItem(Settings settings) {
        super(settings);
    }

    public static ResonanceMode getMode(ItemStack stack) {
        return ResonanceMode.fromId(stack.getOrDefault(LumenComponents.MODE, ResonanceMode.ORE.id()));
    }

    public static int getCharge(ItemStack stack) {
        return ResonanceMath.clamp(stack.getOrDefault(LumenComponents.CHARGE, getCapacity(stack)),
                0, getCapacity(stack));
    }

    public static int getCapacity(ItemStack stack) {
        return ResonanceMath.clamp(stack.getOrDefault(LumenComponents.CAPACITY, 100), 20, 500);
    }

    public static ItemStack getHeldStack(PlayerEntity player) {
        if (player.getMainHandStack().getItem() instanceof ResonatorItem) {
            return player.getMainHandStack();
        }
        if (player.getOffHandStack().getItem() instanceof ResonatorItem) {
            return player.getOffHandStack();
        }
        return ItemStack.EMPTY;
    }

    public static void setModeFromNetwork(ServerPlayerEntity player, int modeId) {
        ItemStack stack = getHeldStack(player);
        if (stack.isEmpty()) {
            return;
        }
        ResonanceMode mode = ResonanceMode.fromId(modeId);
        stack.set(LumenComponents.MODE, mode.id());
        player.playSoundToPlayer(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.PLAYERS, 0.35f, 1.35f);
        player.sendMessage(Text.translatable("message.lumen.mode_changed", mode.name()).formatted(Formatting.GRAY), true);
    }

    private static void setCharge(ItemStack stack, int charge) {
        stack.set(LumenComponents.CHARGE, ResonanceMath.clamp(charge, 0, getCapacity(stack)));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player) || player.isSpectator()) {
            return TypedActionResult.fail(stack);
        }

        LumenConfig config = Lumen.config();
        boolean deep = player.isSneaking();
        int cost = deep ? config.deepScanCost : config.scanCost;
        if (!player.isCreative() && getCharge(stack) < cost) {
            player.sendMessage(Text.translatable("message.lumen.no_charge", cost).formatted(Formatting.RED), true);
            player.playSoundToPlayer(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING_STOP, SoundCategory.PLAYERS, 0.4f, 0.8f);
            return TypedActionResult.fail(stack);
        }

        ResonanceScanner.scan(player, stack, deep);
        if (!player.isCreative()) {
            setCharge(stack, getCharge(stack) - cost);
            player.getItemCooldownManager().set(this, deep ? config.deepCooldownTicks : config.cooldownTicks);
        }
        player.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient() || !(entity instanceof PlayerEntity)) {
            return;
        }
        LumenConfig config = Lumen.config();
        if (stack.get(LumenComponents.CAPACITY) == null) {
            stack.set(LumenComponents.CAPACITY, config.maxCharge);
            setCharge(stack, config.maxCharge);
        }
        int charge = getCharge(stack);
        if (charge < getCapacity(stack) && world.getTime() % config.rechargeTicks == 0) {
            setCharge(stack, charge + 1);
        }
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return getCharge(stack) < getCapacity(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * getCharge(stack) / getCapacity(stack));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        float charge = (float) getCharge(stack) / getCapacity(stack);
        return MathHelper.hsvToRgb(0.02f + 0.46f * charge, 0.62f, 0.95f);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.lumen.resonator.mode", getMode(stack).name()).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.lumen.resonator.charge", getCharge(stack), getCapacity(stack))
                .formatted(Formatting.GRAY));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("item.lumen.resonator.description").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("item.lumen.resonator.hint_cycle").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.lumen.resonator.hint_scan").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.lumen.resonator.hint_deep").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.lumen.resonator.deep_description").formatted(Formatting.DARK_GRAY));
    }
}
