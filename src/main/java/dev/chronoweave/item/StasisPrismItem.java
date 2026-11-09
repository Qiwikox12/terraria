package dev.chronoweave.item;

import dev.chronoweave.registry.ModDataComponents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Pocket Temporal Stasis Prism:
 * - Use on any non-boss LivingEntity: freezes and stores the entity inside the crystal's DataComponent.
 * - Use on Block: releases the frozen entity at the target position.
 * - Shift + Use in Air: launches the frozen entity forward with kinetic velocity.
 */
public class StasisPrismItem extends Item {
    public StasisPrismItem(Settings settings) {
        super(settings);
    }

    public static boolean hasCapturedEntity(ItemStack stack) {
        NbtCompound nbt = stack.get(ModDataComponents.CAPTURED_ENTITY);
        return nbt != null && nbt.contains("EntityData");
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return hasCapturedEntity(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (hasCapturedEntity(stack)) {
            return ActionResult.PASS;
        }
        if (entity instanceof PlayerEntity || !entity.canUsePortals(false) || entity.getMaxHealth() > 150.0f) {
            if (!user.getWorld().isClient) {
                user.sendMessage(Text.translatable("message.chronoweave.prism_cannot_capture").formatted(Formatting.RED), true);
            }
            return ActionResult.FAIL;
        }
        World world = user.getWorld();
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            NbtCompound entityData = new NbtCompound();
            if (!entity.saveNbt(entityData)) {
                return ActionResult.FAIL;
            }
            entityData.remove("UUID");

            NbtCompound wrapper = new NbtCompound();
            wrapper.put("EntityData", entityData);
            wrapper.putString("DisplayName", entity.getDisplayName().getString());
            wrapper.putFloat("Health", entity.getHealth());
            wrapper.putFloat("MaxHealth", entity.getMaxHealth());

            ItemStack updated = stack.copy();
            updated.set(ModDataComponents.CAPTURED_ENTITY, wrapper);
            user.setStackInHand(hand, updated);

            serverWorld.spawnParticles(ParticleTypes.END_ROD,
                    entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    28, 0.35, 0.45, 0.35, 0.08);
            world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 1.2f, 1.4f);
            user.sendMessage(Text.translatable("message.chronoweave.prism_captured", entity.getDisplayName())
                    .formatted(Formatting.AQUA), true);
            entity.discard();
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        ItemStack stack = context.getStack();
        if (!hasCapturedEntity(stack)) {
            return ActionResult.PASS;
        }
        World world = context.getWorld();
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
            Vec3d exactPos = Vec3d.ofBottomCenter(spawnPos);
            if (releaseEntity(serverWorld, stack, exactPos, Vec3d.ZERO)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendMessage(Text.translatable("message.chronoweave.prism_released")
                            .formatted(Formatting.GREEN), true);
                }
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking() && hasCapturedEntity(stack)) {
            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                Vec3d spawnPos = user.getEyePos().add(user.getRotationVec(1.0f).multiply(1.5));
                Vec3d launchVel = user.getRotationVec(1.0f).multiply(1.45).add(0.0, 0.2, 0.0);
                if (releaseEntity(serverWorld, stack, spawnPos, launchVel)) {
                    user.sendMessage(Text.translatable("message.chronoweave.prism_launched")
                            .formatted(Formatting.GOLD), true);
                    return TypedActionResult.success(stack);
                }
            }
            return TypedActionResult.success(stack, world.isClient());
        }
        return TypedActionResult.pass(stack);
    }

    private static boolean releaseEntity(ServerWorld world, ItemStack stack, Vec3d pos, Vec3d velocity) {
        NbtCompound wrapper = stack.get(ModDataComponents.CAPTURED_ENTITY);
        if (wrapper == null || !wrapper.contains("EntityData")) {
            return false;
        }
        NbtCompound entityData = wrapper.getCompound("EntityData").copy();
        Entity loaded = EntityType.loadEntityWithPassengers(entityData, world, entity -> {
            entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, entity.getYaw(), entity.getPitch());
            entity.setVelocity(velocity);
            entity.velocityModified = true;
            return entity;
        });
        if (loaded == null) {
            return false;
        }
        world.spawnNewEntityAndPassengers(loaded);
        stack.remove(ModDataComponents.CAPTURED_ENTITY);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 0.6, pos.z, 25, 0.3, 0.3, 0.3, 0.08);
        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 1.2f);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound wrapper = stack.get(ModDataComponents.CAPTURED_ENTITY);
        if (wrapper != null && wrapper.contains("DisplayName")) {
            String name = wrapper.getString("DisplayName");
            float hp = wrapper.getFloat("Health");
            float maxHp = wrapper.getFloat("MaxHealth");
            tooltip.add(Text.translatable("tooltip.chronoweave.stasis_prism.occupied",
                    name, String.format("%.1f", hp), String.format("%.1f", maxHp)).formatted(Formatting.AQUA));
            tooltip.add(Text.translatable("tooltip.chronoweave.stasis_prism.release_hint").formatted(Formatting.GOLD));
        } else {
            tooltip.add(Text.translatable("tooltip.chronoweave.stasis_prism.empty").formatted(Formatting.GRAY));
        }
    }
}
