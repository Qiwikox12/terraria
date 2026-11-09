package dev.rift;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Targets {
    private Targets() { }

    public static boolean canAffect(LivingEntity entity) {
        if (!entity.isAlive() || entity.isInvulnerable() || entity.isSpectator() || entity.hasCustomName()
                || entity instanceof PlayerEntity || entity instanceof ArmorStandEntity
                || entity instanceof WitherEntity || entity instanceof EnderDragonEntity
                || entity instanceof MerchantEntity || entity instanceof GolemEntity
                || entity instanceof TameableEntity tameable && tameable.isTamed()) return false;
        return entity instanceof Monster || RiftMod.config().affectPassiveMobs;
    }

    public static boolean visible(ServerWorld world, Vec3d from, LivingEntity target, PlayerEntity owner) {
        Vec3d to = target.getPos().add(0, target.getHeight() * .5, 0);
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, owner)).getType() == HitResult.Type.MISS;
    }

    public static List<LivingEntity> nearby(ServerWorld world, Vec3d center, double radius, PlayerEntity owner) {
        return world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius),
                        entity -> canAffect(entity) && entity.squaredDistanceTo(center) <= radius * radius
                                && visible(world, center, entity, owner)).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center)))
                .limit(32).toList();
    }

    public static int shockwave(ServerWorld world, Vec3d center, PlayerEntity owner,
                               double radius, float damage, double strength, Set<UUID> excluded) {
        int hits = 0;
        for (LivingEntity entity : nearby(world, center, radius, owner)) {
            if (excluded.contains(entity.getUuid())) continue;
            entity.damage(owner.getDamageSources().playerAttack(owner), damage * RiftMod.config().damageMultiplier);
            Vec3d direction = entity.getPos().subtract(center).multiply(1, 0, 1);
            if (direction.lengthSquared() < .01) direction = owner.getRotationVec(1).multiply(1, 0, 1);
            entity.setVelocity(direction.normalize().multiply(strength).add(0, .55, 0));
            entity.velocityModified = true;
            entity.fallDistance = 0;
            hits++;
        }
        return hits;
    }
}
