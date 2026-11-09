package dev.chronoweave.item;

import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.core.ParadoxEchoManager;
import dev.chronoweave.core.TemporalHistoryManager;
import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Riftblade "Singularity Echo":
 * - Melee Hits: Inflict Temporal Instability and stack a Paradox Mark that detonates after 1.75s for 45%-90% bonus echo damage.
 * - Right-Click: Phase Blink Strike — dashes up to 14 blocks along line of sight, slashing all enemies in path & siphoning Chronon energy.
 * - Shift + Right-Click: Spawns a Singularity Gravity Vortex ahead that pulls enemies together before detonating.
 */
public class RiftbladeItem extends SwordItem implements ChrononChargeable {
    public static final int MAX_CAPACITY = 1500;
    public static final int BLINK_COST = 90;
    public static final int VORTEX_COST = 220;

    public RiftbladeItem(Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 4, -2.2f)));
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
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient) {
            // Siphon +15 Chronon per melee strike
            ChrononEnergyHelper.addCharge(stack, 15, MAX_CAPACITY);
            target.addStatusEffect(new StatusEffectInstance(ModStatusEffects.TEMPORAL_INSTABILITY, 45, 0));
            ParadoxEchoManager.ParadoxMark mark = ParadoxEchoManager.recordStrike(
                    target.getUuid(),
                    attacker.getUuid(),
                    9.0f,
                    attacker.getWorld().getTime(),
                    35
            );
            if (attacker instanceof PlayerEntity player && mark.getComboHits() >= 2) {
                player.sendMessage(Text.translatable("message.chronoweave.paradox_combo", mark.getComboHits())
                        .formatted(Formatting.LIGHT_PURPLE), true);
            }
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }

        if (user.isSneaking()) {
            // Deploy Singularity Gravity Vortex
            if (!ChrononEnergyHelper.tryConsume(stack, user, VORTEX_COST)) {
                user.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", VORTEX_COST).formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            Vec3d eye = user.getEyePos();
            Vec3d target = eye.add(user.getRotationVec(1.0f).multiply(7.5));
            BlockHitResult hit = world.raycast(new RaycastContext(
                    eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
            Vec3d vortexCenter = hit.getType() == HitResult.Type.MISS
                    ? target
                    : hit.getPos().subtract(user.getRotationVec(1.0f).multiply(0.75));

            TemporalHistoryManager.spawnSingularityVortex(serverWorld, user.getUuid(), vortexCenter, 6.5, 65);
            user.getItemCooldownManager().set(this, 70);
            user.sendMessage(Text.translatable("message.chronoweave.vortex_spawned").formatted(Formatting.DARK_PURPLE), true);
            return TypedActionResult.success(stack);
        }

        // Phase Blink Strike along line of sight
        if (!ChrononEnergyHelper.tryConsume(stack, user, BLINK_COST)) {
            user.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", BLINK_COST).formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }

        Vec3d startPos = user.getPos();
        Vec3d eyePos = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0f);
        double maxDist = 14.0;
        BlockHitResult ray = world.raycast(new RaycastContext(
                eyePos, eyePos.add(look.multiply(maxDist)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));

        Vec3d dest = ray.getType() == HitResult.Type.MISS
                ? startPos.add(look.multiply(maxDist))
                : ray.getPos().subtract(look.multiply(0.85));

        // Slash all hostile or targetable LivingEntities along the blink corridor
        Set<UUID> slashedIds = new HashSet<>();
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3d point = startPos.lerp(dest, t);
            serverWorld.spawnParticles(ParticleTypes.END_ROD, point.x, point.y + 1.0, point.z, 2, 0.1, 0.1, 0.1, 0.02);
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + 0.9, point.z, 2, 0.15, 0.15, 0.15, 0.01);

            for (Entity entity : serverWorld.getOtherEntities(user, Box.of(point, 2.6, 2.8, 2.6))) {
                if (entity instanceof LivingEntity living && living.isAlive() && slashedIds.add(living.getUuid())) {
                    living.damage(serverWorld.getDamageSources().playerAttack(user), 9.0f);
                    living.setVelocity(living.getVelocity().x * 0.3, 0.42, living.getVelocity().z * 0.3);
                    living.velocityModified = true;
                    living.addStatusEffect(new StatusEffectInstance(ModStatusEffects.TEMPORAL_INSTABILITY, 60, 0));
                    ParadoxEchoManager.recordStrike(living.getUuid(), user.getUuid(), 9.0f, world.getTime(), 30);
                    ChrononEnergyHelper.addCharge(stack, 25, MAX_CAPACITY);
                }
            }
        }

        serverPlayer.teleport(serverWorld, dest.x, dest.y, dest.z, user.getYaw(), user.getPitch());
        serverPlayer.fallDistance = 0.0f;
        world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.45f);
        world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.1f, 1.25f);
        user.getItemCooldownManager().set(this, 22);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(ChrononEnergyHelper.buildChargeBarTooltip(ChrononEnergyHelper.getCharge(stack), MAX_CAPACITY));
        tooltip.add(Text.translatable("tooltip.chronoweave.riftblade.1").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.chronoweave.riftblade.2", BLINK_COST).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.chronoweave.riftblade.3", VORTEX_COST).formatted(Formatting.LIGHT_PURPLE));
    }
}
