package dev.chronoweave.item;

import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.core.TemporalHistoryManager;
import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/**
 * Pocket Hourglass of Personal Rewind & Bullet-Time Dome:
 * - Right-Click: Rewinds personal state by up to 6 seconds (position, velocity, highest health in window, clears fire),
 *   detonating a kinetic afterimage at the departure spot.
 * - Shift + Right-Click: Deploys a 10-block Time-Stop Dome for 6 seconds that freezes mid-air projectiles & monsters.
 */
public class ChronoglassItem extends Item implements ChrononChargeable {
    public static final int MAX_CAPACITY = 1200;
    public static final int REWIND_COST = 200;
    public static final int DOME_COST = 350;

    public ChronoglassItem(Settings settings) {
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

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.pass(stack);
        }

        if (user.isSneaking()) {
            // Deploy Time-Stop Bullet-Time Dome
            if (!ChrononEnergyHelper.tryConsume(stack, user, DOME_COST)) {
                user.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", DOME_COST).formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            TemporalHistoryManager.spawnTimeStopDome(serverWorld, user.getUuid(), user.getPos(), 10.0, 120);
            user.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 140, 1));
            user.getItemCooldownManager().set(this, 100);
            user.sendMessage(Text.translatable("message.chronoweave.time_dome_deployed").formatted(Formatting.AQUA), true);
            return TypedActionResult.success(stack);
        }

        // Personal 6-second Rewind
        Optional<TemporalHistoryManager.TemporalSnapshot> snapshotOpt =
                TemporalHistoryManager.popBestRewindSnapshot(user.getUuid(), world.getRegistryKey());
        if (snapshotOpt.isEmpty()) {
            user.sendMessage(Text.translatable("message.chronoweave.no_rewind_history").formatted(Formatting.YELLOW), true);
            return TypedActionResult.fail(stack);
        }
        if (!ChrononEnergyHelper.tryConsume(stack, user, REWIND_COST)) {
            user.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", REWIND_COST).formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }

        TemporalHistoryManager.TemporalSnapshot snap = snapshotOpt.get();
        Vec3d departurePos = user.getPos();

        // Detonate a temporal afterimage at departure position to punish pursuing enemies
        serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, departurePos.x, departurePos.y + 1.0, departurePos.z, 40, 0.4, 0.5, 0.4, 0.15);
        serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM, departurePos.x, departurePos.y + 1.0, departurePos.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (Entity nearby : serverWorld.getOtherEntities(user, Box.of(departurePos, 7.0, 5.0, 7.0))) {
            if (nearby instanceof LivingEntity monster && nearby instanceof Monster) {
                monster.damage(serverWorld.getDamageSources().indirectMagic(user, user), 8.0f);
                Vec3d knock = monster.getPos().subtract(departurePos).normalize().multiply(0.95);
                monster.setVelocity(knock.x, 0.45, knock.z);
                monster.velocityModified = true;
                monster.addStatusEffect(new StatusEffectInstance(ModStatusEffects.TIME_LOCK, 60, 0));
            }
        }

        // Teleport and restore state
        serverPlayer.teleport(serverWorld, snap.x(), snap.y(), snap.z(), snap.yaw(), snap.pitch());
        serverPlayer.setVelocity(new Vec3d(snap.vx(), snap.vy(), snap.vz()));
        serverPlayer.velocityModified = true;
        serverPlayer.fallDistance = 0.0f;
        serverPlayer.extinguish();
        if (snap.health() > serverPlayer.getHealth()) {
            serverPlayer.setHealth(Math.min(serverPlayer.getMaxHealth(), snap.health()));
        }
        serverPlayer.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 100, 0));

        serverWorld.spawnParticles(ParticleTypes.END_ROD, snap.x(), snap.y() + 1.0, snap.z(), 30, 0.35, 0.5, 0.35, 0.08);
        world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.1f, 1.45f);
        world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 1.3f);

        user.getItemCooldownManager().set(this, 50);
        user.sendMessage(Text.translatable("message.chronoweave.rewind_complete", String.format("%.1f", serverPlayer.getHealth()))
                .formatted(Formatting.GOLD), true);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(ChrononEnergyHelper.buildChargeBarTooltip(ChrononEnergyHelper.getCharge(stack), MAX_CAPACITY));
        tooltip.add(Text.translatable("tooltip.chronoweave.chronoglass.1", REWIND_COST).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.chronoweave.chronoglass.2", DOME_COST).formatted(Formatting.LIGHT_PURPLE));
    }
}
