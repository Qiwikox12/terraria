package dev.chronoweave.core;

import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages rolling player history snapshots for Temporal Rewind, active Time-Stop Domes,
 * and Singularity Gravity Vortices.
 */
public final class TemporalHistoryManager {
    public static final int MAX_SNAPSHOTS = 24; // 24 snapshots * 5 ticks = 120 ticks (6 seconds)

    public record TemporalSnapshot(
            long worldTick,
            RegistryKey<World> dimension,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            float yaw,
            float pitch,
            float health,
            int foodLevel,
            float saturation
    ) {
    }

    public static final class TimeStopDome {
        private final UUID ownerId;
        private final RegistryKey<World> dimension;
        private final Vec3d center;
        private final double radius;
        private final long expireTick;
        private final Map<UUID, Vec3d> frozenProjectileVelocities = new ConcurrentHashMap<>();

        public TimeStopDome(UUID ownerId, RegistryKey<World> dimension, Vec3d center, double radius, long expireTick) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.expireTick = expireTick;
        }
    }

    public static final class SingularityVortex {
        private final UUID ownerId;
        private final RegistryKey<World> dimension;
        private final Vec3d center;
        private final double radius;
        private final long expireTick;

        public SingularityVortex(UUID ownerId, RegistryKey<World> dimension, Vec3d center, double radius, long expireTick) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.expireTick = expireTick;
        }
    }

    private static final Map<UUID, Deque<TemporalSnapshot>> PLAYER_HISTORY = new ConcurrentHashMap<>();
    private static final List<TimeStopDome> ACTIVE_DOMES = new ArrayList<>();
    private static final List<SingularityVortex> ACTIVE_VORTICES = new ArrayList<>();

    private TemporalHistoryManager() {
    }

    public static void recordSnapshot(UUID playerId, TemporalSnapshot snapshot) {
        Deque<TemporalSnapshot> deque = PLAYER_HISTORY.computeIfAbsent(playerId, k -> new ArrayDeque<>(MAX_SNAPSHOTS + 1));
        synchronized (deque) {
            deque.addLast(snapshot);
            while (deque.size() > MAX_SNAPSHOTS) {
                deque.removeFirst();
            }
        }
    }

    public static void recordPlayerState(ServerPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        recordSnapshot(player.getUuid(), new TemporalSnapshot(
                player.getServerWorld().getTime(),
                player.getWorld().getRegistryKey(),
                player.getX(),
                player.getY(),
                player.getZ(),
                vel.x,
                vel.y,
                vel.z,
                player.getYaw(),
                player.getPitch(),
                player.getHealth(),
                player.getHungerManager().getFoodLevel(),
                player.getHungerManager().getSaturationLevel()
        ));
    }

    /**
     * Retrieves and consumes the oldest available snapshot in the same dimension (or the highest-HP recent snapshot
     * if the player just suffered catastrophic damage).
     */
    public static Optional<TemporalSnapshot> popBestRewindSnapshot(UUID playerId, RegistryKey<World> currentDimension) {
        Deque<TemporalSnapshot> deque = PLAYER_HISTORY.get(playerId);
        if (deque == null) {
            return Optional.empty();
        }
        synchronized (deque) {
            if (deque.isEmpty()) {
                return Optional.empty();
            }
            TemporalSnapshot oldestSameDim = null;
            TemporalSnapshot highestHealthSnapshot = null;
            for (TemporalSnapshot snap : deque) {
                if (snap.dimension().equals(currentDimension)) {
                    if (oldestSameDim == null) {
                        oldestSameDim = snap;
                    }
                    if (highestHealthSnapshot == null || snap.health() > highestHealthSnapshot.health()) {
                        highestHealthSnapshot = snap;
                    }
                }
            }
            deque.clear();
            if (oldestSameDim == null) {
                return Optional.empty();
            }
            // Preserve whichever health was higher between the earliest window and peak health in the last 6s
            float restoredHealth = highestHealthSnapshot != null
                    ? Math.max(oldestSameDim.health(), highestHealthSnapshot.health())
                    : oldestSameDim.health();
            return Optional.of(new TemporalSnapshot(
                    oldestSameDim.worldTick(),
                    oldestSameDim.dimension(),
                    oldestSameDim.x(),
                    oldestSameDim.y(),
                    oldestSameDim.z(),
                    oldestSameDim.vx(),
                    oldestSameDim.vy(),
                    oldestSameDim.vz(),
                    oldestSameDim.yaw(),
                    oldestSameDim.pitch(),
                    restoredHealth,
                    oldestSameDim.foodLevel(),
                    oldestSameDim.saturation()
            ));
        }
    }

    public static int getSnapshotCount(UUID playerId) {
        Deque<TemporalSnapshot> deque = PLAYER_HISTORY.get(playerId);
        return deque == null ? 0 : deque.size();
    }

    public static void spawnTimeStopDome(ServerWorld world, UUID ownerId, Vec3d center, double radius, int durationTicks) {
        synchronized (ACTIVE_DOMES) {
            ACTIVE_DOMES.add(new TimeStopDome(ownerId, world.getRegistryKey(), center, radius, world.getTime() + durationTicks));
        }
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.4f, 0.55f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.2f, 1.6f);
    }

    public static void spawnSingularityVortex(ServerWorld world, UUID ownerId, Vec3d center, double radius, int durationTicks) {
        synchronized (ACTIVE_VORTICES) {
            ACTIVE_VORTICES.add(new SingularityVortex(ownerId, world.getRegistryKey(), center, radius, world.getTime() + durationTicks));
        }
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.7f, 1.75f);
    }

    public static void tickServerWorld(ServerWorld world) {
        long now = world.getTime();
        if (now % 5 == 0) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isAlive()) {
                    recordPlayerState(player);
                }
            }
        }

        tickTimeStopDomes(world, now);
        tickSingularityVortices(world, now);
    }

    private static void tickTimeStopDomes(ServerWorld world, long now) {
        synchronized (ACTIVE_DOMES) {
            Iterator<TimeStopDome> it = ACTIVE_DOMES.iterator();
            while (it.hasNext()) {
                TimeStopDome dome = it.next();
                if (!dome.dimension.equals(world.getRegistryKey())) {
                    continue;
                }
                Box box = Box.of(dome.center, dome.radius * 2.0, dome.radius * 2.0, dome.radius * 2.0);
                if (now >= dome.expireTick) {
                    releaseDomeProjectiles(world, dome);
                    it.remove();
                    continue;
                }

                // Render geodesic particle rings on dome boundary
                if (now % 3 == 0) {
                    int points = 24;
                    double phase = (now * 0.08) % (Math.PI * 2.0);
                    for (int i = 0; i < points; i++) {
                        double angle = phase + (Math.PI * 2.0 * i) / points;
                        double rx = dome.center.x + Math.cos(angle) * dome.radius;
                        double rz = dome.center.z + Math.sin(angle) * dome.radius;
                        world.spawnParticles(ParticleTypes.END_ROD, rx, dome.center.y + 0.2, rz, 1, 0.0, 0.0, 0.0, 0.0);
                        double ry = dome.center.y + Math.sin(angle) * (dome.radius * 0.65);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, rx, ry, dome.center.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }

                // Freeze projectiles and hostile entities within the spherical radius
                for (Entity entity : world.getOtherEntities(null, box)) {
                    if (entity.getUuid().equals(dome.ownerId)) {
                        continue;
                    }
                    if (entity.getPos().squaredDistanceTo(dome.center) > dome.radius * dome.radius) {
                        continue;
                    }
                    if (entity instanceof ProjectileEntity projectile) {
                        dome.frozenProjectileVelocities.computeIfAbsent(projectile.getUuid(), id -> projectile.getVelocity());
                        projectile.setNoGravity(true);
                        projectile.setVelocity(Vec3d.ZERO);
                        projectile.velocityModified = true;
                    } else if (entity instanceof LivingEntity living && !(living instanceof ServerPlayerEntity)) {
                        living.setVelocity(0.0, Math.min(0.0, living.getVelocity().y * 0.05), 0.0);
                        living.velocityModified = true;
                        living.addStatusEffect(new StatusEffectInstance(ModStatusEffects.TIME_LOCK, 10, 0, true, false, true));
                    }
                }
            }
        }
    }

    private static void releaseDomeProjectiles(ServerWorld world, TimeStopDome dome) {
        for (Map.Entry<UUID, Vec3d> entry : dome.frozenProjectileVelocities.entrySet()) {
            Entity entity = world.getEntity(entry.getKey());
            if (entity instanceof ProjectileEntity projectile && entity.isAlive()) {
                projectile.setNoGravity(false);
                Vec3d awayFromCenter = projectile.getPos().subtract(dome.center).normalize();
                double speed = Math.max(0.85, entry.getValue().length() * 1.35);
                projectile.setVelocity(awayFromCenter.multiply(speed));
                projectile.velocityModified = true;
            }
        }
        world.playSound(null, dome.center.x, dome.center.y, dome.center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.7f, 1.65f);
        world.spawnParticles(ParticleTypes.FLASH, dome.center.x, dome.center.y + 1.0, dome.center.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void tickSingularityVortices(ServerWorld world, long now) {
        synchronized (ACTIVE_VORTICES) {
            Iterator<SingularityVortex> it = ACTIVE_VORTICES.iterator();
            while (it.hasNext()) {
                SingularityVortex vortex = it.next();
                if (!vortex.dimension.equals(world.getRegistryKey())) {
                    continue;
                }
                Box box = Box.of(vortex.center, vortex.radius * 2.0, vortex.radius * 2.0, vortex.radius * 2.0);
                if (now >= vortex.expireTick) {
                    detonateVortex(world, vortex, box);
                    it.remove();
                    continue;
                }

                // Spiral particle accretion disk
                for (int i = 0; i < 6; i++) {
                    double angle = (now * 0.35) + (i * Math.PI / 3.0);
                    double dist = ((60 - (vortex.expireTick - now)) % 20) / 20.0 * vortex.radius;
                    double px = vortex.center.x + Math.cos(angle) * dist;
                    double pz = vortex.center.z + Math.sin(angle) * dist;
                    world.spawnParticles(ParticleTypes.PORTAL, px, vortex.center.y + 0.5, pz, 2, 0.05, 0.05, 0.05, 0.1);
                }
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, vortex.center.x, vortex.center.y + 0.5, vortex.center.z, 4, 0.15, 0.15, 0.15, 0.02);

                for (Entity entity : world.getOtherEntities(null, box)) {
                    if (entity.getUuid().equals(vortex.ownerId) || entity instanceof ServerPlayerEntity) {
                        continue;
                    }
                    if (entity instanceof Monster || entity instanceof ItemEntity || (entity instanceof LivingEntity && !(entity instanceof ServerPlayerEntity))) {
                        Vec3d pull = vortex.center.subtract(entity.getPos());
                        double distance = Math.max(0.5, pull.length());
                        if (distance <= vortex.radius) {
                            Vec3d pullVel = pull.normalize().multiply(Math.min(0.45, 1.2 / distance));
                            entity.setVelocity(entity.getVelocity().multiply(0.5).add(pullVel.x, pullVel.y * 0.5 + 0.04, pullVel.z));
                            entity.velocityModified = true;
                        }
                    }
                }
            }
        }
    }

    private static void detonateVortex(ServerWorld world, SingularityVortex vortex, Box box) {
        world.spawnParticles(ParticleTypes.SONIC_BOOM, vortex.center.x, vortex.center.y + 0.5, vortex.center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, vortex.center.x, vortex.center.y + 0.5, vortex.center.z, 45, 0.5, 0.5, 0.5, 0.15);
        world.playSound(null, vortex.center.x, vortex.center.y, vortex.center.z,
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.9f, 1.3f);

        Entity owner = vortex.ownerId != null ? world.getEntity(vortex.ownerId) : null;
        for (Entity entity : world.getOtherEntities(null, box)) {
            if (entity instanceof LivingEntity living && !entity.getUuid().equals(vortex.ownerId) && !(entity instanceof ServerPlayerEntity)) {
                if (living.getPos().squaredDistanceTo(vortex.center) <= vortex.radius * vortex.radius) {
                    if (owner instanceof LivingEntity livingOwner) {
                        living.damage(world.getDamageSources().indirectMagic(livingOwner, livingOwner), 10.0f);
                    } else {
                        living.damage(world.getDamageSources().magic(), 10.0f);
                    }
                    // Launch upward into brief zero-gravity suspension
                    living.setVelocity(living.getVelocity().x * 0.2, 0.65, living.getVelocity().z * 0.2);
                    living.velocityModified = true;
                }
            }
        }
    }
}
