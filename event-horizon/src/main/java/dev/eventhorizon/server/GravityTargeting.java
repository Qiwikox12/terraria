package dev.eventhorizon.server;

import dev.eventhorizon.EventHorizon;
import dev.eventhorizon.config.GravityConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class GravityTargeting {
    public static final TagKey<net.minecraft.entity.EntityType<?>> IMMUNE =
        TagKey.of(RegistryKeys.ENTITY_TYPE, EventHorizon.id("gravity_immune"));

    public static boolean eligible(Entity entity, ServerPlayerEntity caster, GravityConfig config, boolean items) {
        if (entity == caster || !entity.isAlive() || entity.isSpectator() || entity.getType().isIn(IMMUNE)
            || entity.isTeammate(caster) || entity.hasVehicle() || entity.hasPassengers()) return false;
        if (entity instanceof ItemEntity) return items;
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity
            || entity instanceof TameableEntity || entity instanceof AbstractHorseEntity) return false;
        if (entity instanceof PlayerEntity player) {
            return config.allowPlayerTargets && !player.isCreative() && caster.getServer().isPvpEnabled()
                && caster.shouldDamagePlayer(player);
        }
        return config.affectPassiveMobs || entity instanceof Monster
            || entity instanceof MobEntity mob && mob.getTarget() == caster;
    }

    public static boolean visible(ServerWorld world, Vec3d from, Entity target, Entity caster) {
        return world.raycast(new RaycastContext(from, target.getPos().add(0, target.getHeight() * 0.5, 0),
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster)).getType() == HitResult.Type.MISS;
    }

    public static List<Entity> targets(ServerPlayerEntity caster, Vec3d center, double radius, boolean items) {
        ServerWorld world = caster.getServerWorld();
        GravityConfig config = EventHorizon.CONFIG;
        return world.getOtherEntities(caster, Box.of(center, radius * 2, radius * 2, radius * 2),
                entity -> eligible(entity, caster, config, items) && entity.squaredDistanceTo(center) <= radius * radius)
            .stream().sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center)))
            .filter(entity -> visible(world, center, entity, caster)).limit(config.maxAffectedEntities).toList();
    }

    public static Vec3d aim(ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        HitResult hit = player.getServerWorld().raycast(new RaycastContext(eye,
            eye.add(player.getRotationVec(1).multiply(18)), RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, player));
        return hit.getPos().subtract(player.getRotationVec(1).multiply(0.4));
    }

    public static Optional<Vec3d> blinkDestination(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d direction = player.getRotationVec(1);
        for (double distance = 12; distance >= 2; distance -= 0.5) {
            Vec3d projected = player.getPos().add(direction.multiply(distance));
            BlockPos base = BlockPos.ofFloored(projected.add(0, -0.01, 0));
            for (int down = 0; down <= 3; down++) {
                BlockPos floor = base.down(down);
                if (!world.isChunkLoaded(floor) || floor.getY() < world.getBottomY()) break;
                BlockState support = world.getBlockState(floor);
                if (!support.isSideSolidFullSquare(world, floor, Direction.UP) || dangerous(support)) continue;
                Vec3d destination = new Vec3d(projected.x, floor.getY() + 1, projected.z);
                if (destination.squaredDistanceTo(player.getPos()) < 4 || destination.squaredDistanceTo(player.getPos()) > 144) continue;
                if (clearPath(player, destination) && safeLanding(world, player, destination)) return Optional.of(destination);
            }
        }
        return Optional.empty();
    }

    private static boolean clearPath(ServerPlayerEntity player, Vec3d end) {
        Vec3d delta = end.subtract(player.getPos());
        int steps = (int) Math.ceil(delta.length() * 4);
        for (int step = 1; step <= steps; step++) {
            Vec3d position = player.getPos().add(delta.multiply((double) step / steps));
            if (!safeLanding(player.getServerWorld(), player, position)) return false;
        }
        return true;
    }

    private static boolean safeLanding(ServerWorld world, ServerPlayerEntity player, Vec3d position) {
        Box box = player.getBoundingBox().offset(position.subtract(player.getPos()));
        if (!world.getWorldBorder().contains(box) || position.y < world.getBottomY() || box.maxY >= world.getTopY()) return false;
        for (BlockPos block : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (!world.isChunkLoaded(block) || dangerous(world.getBlockState(block))) return false;
        }
        return world.isSpaceEmpty(player, box);
    }

    private static boolean dangerous(BlockState state) {
        return !state.getFluidState().isEmpty() || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
            || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE)
            || state.isOf(Blocks.SOUL_CAMPFIRE) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.POINTED_DRIPSTONE);
    }
}
