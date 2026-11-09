package dev.chronoweave.item;

import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.effect.ModStatusEffects;
import dev.chronoweave.registry.ModDataComponents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Wormhole Astrolabe: 4-slot cross-dimensional spatial teleporter.
 * - Sneak + Right-Click Block: Cycles active slot (1..4).
 * - Sneak + Right-Click Air: Binds current coordinates & dimension into the active slot.
 * - Right-Click Air: Opens a Wormhole Gate and teleports to the active waypoint (even across dimensions).
 */
public class SpatialCompassItem extends Item implements ChrononChargeable {
    public static final int MAX_CAPACITY = 1600;
    public static final int TELEPORT_COST = 200;
    public static final int SLOT_COUNT = 4;

    public SpatialCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getMaxChrononCapacity() {
        return MAX_CAPACITY;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return ChrononEnergyHelper.getBarStep(stack, MAX_CAPACITY);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return ChrononEnergyHelper.getBarColor(stack, MAX_CAPACITY);
    }

    private static int getActiveSlot(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(ModDataComponents.ACTIVE_WAYPOINT_SLOT, 0), SLOT_COUNT);
    }

    private static List<String> getMutableWaypoints(ItemStack stack) {
        List<String> existing = stack.get(ModDataComponents.SPATIAL_WAYPOINTS);
        List<String> list = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (existing != null && i < existing.size()) {
                list.add(existing.get(i));
            } else {
                list.add("");
            }
        }
        return list;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player != null && player.isSneaking()) {
            ItemStack stack = context.getStack();
            if (!context.getWorld().isClient) {
                int nextSlot = (getActiveSlot(stack) + 1) % SLOT_COUNT;
                stack.set(ModDataComponents.ACTIVE_WAYPOINT_SLOT, nextSlot);
                player.sendMessage(Text.translatable("message.chronoweave.astrolabe_slot_selected", nextSlot + 1)
                        .formatted(Formatting.AQUA), true);
                context.getWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }

        int slot = getActiveSlot(stack);
        List<String> waypoints = getMutableWaypoints(stack);

        if (user.isSneaking()) {
            // Bind current position to active slot
            String dimId = world.getRegistryKey().getValue().toString();
            String encoded = String.format("%s|%.2f|%.2f|%.2f|%.1f|%.1f",
                    dimId, user.getX(), user.getY(), user.getZ(), user.getYaw(), user.getPitch());
            waypoints.set(slot, encoded);
            stack.set(ModDataComponents.SPATIAL_WAYPOINTS, waypoints);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_LODESTONE_PLACE, SoundCategory.PLAYERS, 1.0f, 1.35f);
            user.sendMessage(Text.translatable("message.chronoweave.astrolabe_bound",
                            slot + 1, user.getBlockX(), user.getBlockY(), user.getBlockZ(), dimId)
                    .formatted(Formatting.GOLD), true);
            return TypedActionResult.success(stack);
        }

        String entry = waypoints.get(slot);
        if (entry == null || entry.isBlank()) {
            user.sendMessage(Text.translatable("message.chronoweave.astrolabe_slot_empty", slot + 1)
                    .formatted(Formatting.YELLOW), true);
            return TypedActionResult.fail(stack);
        }

        String[] parts = entry.split("\\|");
        if (parts.length < 6) {
            return TypedActionResult.fail(stack);
        }
        Identifier dimIdentifier = Identifier.tryParse(parts[0]);
        if (dimIdentifier == null || serverPlayer.getServer() == null) {
            return TypedActionResult.fail(stack);
        }
        RegistryKey<World> targetKey = RegistryKey.of(RegistryKeys.WORLD, dimIdentifier);
        ServerWorld targetWorld = serverPlayer.getServer().getWorld(targetKey);
        if (targetWorld == null) {
            return TypedActionResult.fail(stack);
        }

        if (!ChrononEnergyHelper.tryConsume(stack, user, TELEPORT_COST)) {
            user.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", TELEPORT_COST)
                    .formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }

        double tx = Double.parseDouble(parts[1]);
        double ty = Double.parseDouble(parts[2]);
        double tz = Double.parseDouble(parts[3]);
        float tyaw = Float.parseFloat(parts[4]);
        float tpitch = Float.parseFloat(parts[5]);

        ServerWorld originWorld = serverPlayer.getServerWorld();
        originWorld.spawnParticles(ParticleTypes.PORTAL, user.getX(), user.getBodyY(0.5), user.getZ(), 40, 0.35, 0.5, 0.35, 0.25);
        originWorld.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.5f, 1.5f);

        serverPlayer.teleport(targetWorld, tx, ty, tz, tyaw, tpitch);
        serverPlayer.fallDistance = 0.0f;
        serverPlayer.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 120, 0));
        serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false, true));

        targetWorld.spawnParticles(ParticleTypes.END_ROD, tx, ty + 1.0, tz, 35, 0.4, 0.6, 0.4, 0.08);
        targetWorld.playSound(null, BlockPos.ofFloored(tx, ty, tz), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.1f, 1.25f);
        user.getItemCooldownManager().set(this, 60);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(ChrononEnergyHelper.buildChargeBarTooltip(ChrononEnergyHelper.getCharge(stack), MAX_CAPACITY));
        int activeSlot = getActiveSlot(stack);
        List<String> waypoints = getMutableWaypoints(stack);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String raw = waypoints.get(i);
            Formatting color = (i == activeSlot) ? Formatting.AQUA : Formatting.GRAY;
            String prefix = (i == activeSlot) ? "▶ " : "  ";
            if (raw == null || raw.isBlank()) {
                tooltip.add(Text.literal(prefix + "#" + (i + 1) + ": ").append(Text.translatable("tooltip.chronoweave.astrolabe.unbound")).formatted(color));
            } else {
                String[] p = raw.split("\\|");
                if (p.length >= 4) {
                    tooltip.add(Text.literal(String.format("%s#%d: [%s] %.0f, %.0f, %.0f", prefix, i + 1, p[0],
                            Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]))).formatted(color));
                }
            }
        }
        tooltip.add(Text.translatable("tooltip.chronoweave.astrolabe.hint", TELEPORT_COST).formatted(Formatting.GOLD));
    }
}
