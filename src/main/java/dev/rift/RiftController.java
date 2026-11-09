package dev.rift;

import dev.rift.net.PowerPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RiftController {
    private static final Map<UUID, Runtime> ACTIVE = new HashMap<>();
    private static final Map<UUID, UUID> HELD_BY = new HashMap<>();
    static final int VECTOR_DURATION = 60;
    static final int SURGE_DURATION = 8;
    static final int SINGULARITY_DURATION = 70;

    private RiftController() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) tickPlayer(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanup(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            cleanup(oldPlayer);
            sendState(newPlayer);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
            HELD_BY.clear();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && source.isOf(DamageTypes.FALL)) {
                Runtime runtime = ACTIVE.get(player.getUuid());
                return runtime == null || runtime.fallProtection <= 0;
            }
            return true;
        });
    }

    public static PowerState power(ServerPlayerEntity player) {
        return RiftSaveData.get(player.getServer()).player(player.getUuid());
    }

    private static Runtime runtime(ServerPlayerEntity player) {
        Runtime current = ACTIVE.get(player.getUuid());
        if (current != null && current.world != player.getServerWorld()) cleanup(player);
        return ACTIVE.computeIfAbsent(player.getUuid(), ignored -> new Runtime(player.getServerWorld()));
    }

    public static boolean cycleMode(ServerPlayerEntity player) {
        if (!RiftMod.hasGauntlet(player) || !player.isAlive() || player.isSpectator() || player.isUsingItem()) return false;
        Runtime runtime = runtime(player);
        int now = player.getServer().getTicks();
        if (now - runtime.lastSwitch < 6) return false;
        runtime.lastSwitch = now;
        PowerState state = power(player);
        state.cycleMode();
        RiftSaveData.get(player.getServer()).markDirty();
        syncItemMode(player);
        RiftEffects.sound(player.getServerWorld(), player.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                .5f, .8f + state.mode().ordinal() * .25f);
        player.sendMessage(Text.translatable("message.rift.mode", Text.translatable(state.mode().translationKey()),
                state.mode().cost()).formatted(Formatting.AQUA), true);
        sendState(player);
        return true;
    }

    public static boolean cast(ServerPlayerEntity player) {
        if (!RiftMod.hasGauntlet(player) || !player.isAlive() || player.isSpectator()) return false;
        Runtime runtime = runtime(player);
        if (!runtime.held.isEmpty() || runtime.dashTicks > 0) return false;
        PowerState state = power(player);
        AbilityMode mode = state.mode();
        if (state.cooldown(mode) > 0) {
            feedback(player, "message.rift.cooldown", String.format(java.util.Locale.ROOT, "%.1f", state.cooldown(mode) / 20f));
            return false;
        }
        if (state.energy() < mode.cost()) {
            feedback(player, "message.rift.energy", mode.cost());
            return false;
        }
        boolean success = switch (mode) {
            case VECTOR -> beginVector(player, runtime);
            case SURGE -> beginSurge(player, runtime);
            case SINGULARITY -> beginSingularity(player, runtime);
        };
        if (success) RiftSaveData.get(player.getServer()).markDirty();
        sendState(player);
        return success;
    }

    private static boolean beginVector(ServerPlayerEntity player, Runtime runtime) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1);
        List<LivingEntity> candidates = player.getServerWorld()
                .getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(14), entity ->
                        Targets.canAffect(entity) && !HELD_BY.containsKey(entity.getUuid())
                                && entity.squaredDistanceTo(eye) <= 14 * 14 && player.canSee(entity)
                                && entity.getPos().add(0, entity.getHeight() * .5, 0).subtract(eye)
                                .normalize().dotProduct(look) > .65).stream()
                .sorted(Comparator.comparingDouble(player::squaredDistanceTo))
                .limit(RiftMod.config().maxHeldMobs).toList();
        if (candidates.isEmpty()) {
            feedback(player, "message.rift.no_target");
            return false;
        }
        if (!power(player).trySpend(AbilityMode.VECTOR)) return false;
        runtime.held.addAll(candidates);
        runtime.holdTicks = 0;
        candidates.forEach(entity -> HELD_BY.put(entity.getUuid(), player.getUuid()));
        RiftEffects.sound(player.getServerWorld(), player.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, .65f, 1.6f);
        return true;
    }

    private static boolean beginSurge(ServerPlayerEntity player, Runtime runtime) {
        if (player.hasVehicle() || player.isFallFlying() || player.isSleeping()) {
            feedback(player, "message.rift.cannot_surge");
            return false;
        }
        if (!power(player).trySpend(AbilityMode.SURGE)) return false;
        runtime.dashDirection = player.getRotationVec(1).normalize();
        runtime.dashTicks = SURGE_DURATION;
        runtime.previousPos = player.getPos();
        runtime.dashHit.clear();
        runtime.fallProtection = 120;
        pushPlayer(player, runtime.dashDirection.multiply(1.65).add(0, .12, 0));
        RiftEffects.wave(player.getServerWorld(), player.getPos(), 2, AbilityMode.SURGE.color());
        RiftEffects.sound(player.getServerWorld(), player.getPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, .7f, 1.6f);
        return true;
    }

    private static boolean beginSingularity(ServerPlayerEntity player, Runtime runtime) {
        HitResult hit = player.raycast(24, 1, false);
        Vec3d center = hit.getPos();
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            center = center.add(Vec3d.of(block.getSide().getVector()).multiply(.85));
        }
        BlockPos pos = BlockPos.ofFloored(center);
        ServerWorld world = player.getServerWorld();
        if (!world.isChunkLoaded(pos) || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) {
            feedback(player, "message.rift.invalid_position");
            return false;
        }
        if (!power(player).trySpend(AbilityMode.SINGULARITY)) return false;
        runtime.singularity = new Singularity(center);
        RiftEffects.sound(world, center, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, .85f, .65f);
        RiftEffects.ring(world, center, 3, AbilityMode.SINGULARITY.color(), 0);
        return true;
    }

    public static void releaseVector(ServerPlayerEntity player) {
        Runtime runtime = ACTIVE.get(player.getUuid());
        if (runtime == null || runtime.held.isEmpty()) return;
        if (!player.isAlive() || player.isSpectator() || runtime.world != player.getServerWorld()) {
            cancelHold(runtime);
            return;
        }
        Vec3d look = player.getRotationVec(1);
        for (LivingEntity entity : runtime.held) {
            HELD_BY.remove(entity.getUuid());
            if (!entity.isAlive() || entity.getWorld() != runtime.world || !Targets.canAffect(entity)
                    || entity.squaredDistanceTo(player) > 24 * 24 || !player.canSee(entity)) continue;
            entity.damage(player.getDamageSources().playerAttack(player), 18 * RiftMod.config().damageMultiplier);
            entity.setVelocity(look.multiply(2.25).add(0, .4, 0));
            entity.velocityModified = true;
            entity.fallDistance = 0;
            runtime.thrown.add(new Thrown(entity));
        }
        runtime.held.clear();
        RiftEffects.wave(runtime.world, player.getPos().add(look.multiply(3)), 2.5, AbilityMode.VECTOR.color());
        RiftEffects.sound(runtime.world, player.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, .55f, 1.5f);
        sendState(player);
    }

    static void tickPlayer(ServerPlayerEntity player) {
        Runtime runtime = runtime(player);
        PowerState state = power(player);
        if (state.tick(!runtime.held.isEmpty(), RiftMod.config().energyRegenPerSecond / 20)) {
            RiftSaveData.get(player.getServer()).markDirty();
        }
        if (!player.isAlive() || player.isSpectator()) {
            cleanup(player);
            return;
        }
        if (!runtime.held.isEmpty()) tickHold(player, runtime);
        if (runtime.dashTicks > 0) tickSurge(player, runtime);
        if (runtime.fallProtection > 0) {
            player.fallDistance = 0;
            runtime.fallProtection--;
            if (runtime.dashTicks == 0 && player.isOnGround()) runtime.fallProtection = 0;
        }
        tickThrown(player, runtime);
        if (runtime.singularity != null) tickSingularity(player, runtime);
        if (RiftMod.hasGauntlet(player) && player.getServer().getTicks() % 4 == 0) {
            syncItemMode(player);
            sendState(player);
        }
    }

    private static void tickHold(ServerPlayerEntity player, Runtime runtime) {
        if (!player.isUsingItem() || !player.getActiveItem().isOf(RiftMod.GAUNTLET)) {
            releaseVector(player);
            return;
        }
        if (++runtime.holdTicks >= VECTOR_DURATION) {
            player.stopUsingItem();
            return;
        }
        runtime.held.removeIf(entity -> {
            boolean remove = !Targets.canAffect(entity) || entity.getWorld() != runtime.world
                    || entity.squaredDistanceTo(player) > 24 * 24 || !player.canSee(entity);
            if (remove) HELD_BY.remove(entity.getUuid());
            return remove;
        });
        Vec3d eye = player.getEyePos();
        HitResult hit = player.raycast(4.5, 1, false);
        double distance = Math.max(1.5, Math.min(4.5, hit.getPos().distanceTo(eye) - .7));
        Vec3d focus = eye.add(player.getRotationVec(1).multiply(distance));
        for (int i = 0; i < runtime.held.size(); i++) {
            LivingEntity entity = runtime.held.get(i);
            double angle = runtime.holdTicks * .035 + i * Math.PI * 2 / runtime.held.size();
            double spread = runtime.held.size() == 1 ? 0 : 1.25;
            Vec3d target = focus.add(Math.cos(angle) * spread, Math.sin(angle * 2) * .35,
                    Math.sin(angle) * spread).subtract(0, entity.getHeight() * .5, 0);
            Vec3d velocity = target.subtract(entity.getPos()).multiply(.3);
            if (velocity.lengthSquared() > 1.44) velocity = velocity.normalize().multiply(1.2);
            entity.setVelocity(velocity.add(0, .08, 0));
            entity.velocityModified = true;
            entity.fallDistance = 0;
            if (runtime.holdTicks % 6 == 0) RiftEffects.tether(runtime.world,
                    eye.add(player.getRotationVec(1).multiply(1.1)).add(0, -.2, 0),
                    entity.getPos().add(0, entity.getHeight() * .5, 0));
        }
        if (runtime.holdTicks % 6 == 0) RiftEffects.ring(runtime.world, focus, 1.5, AbilityMode.VECTOR.color(), runtime.holdTicks * .1);
        magnet(runtime.world, player.getPos().add(0, .7, 0), 7, .2);
        if (runtime.held.isEmpty()) player.stopUsingItem();
    }

    private static void tickSurge(ServerPlayerEntity player, Runtime runtime) {
        if (!RiftMod.hasGauntlet(player) || player.hasVehicle() || player.isSleeping()
                || player.squaredDistanceTo(runtime.previousPos) > 64) {
            runtime.dashTicks = 0;
            return;
        }
        Box path = new Box(runtime.previousPos, player.getPos()).expand(1.6);
        for (LivingEntity entity : runtime.world.getEntitiesByClass(LivingEntity.class, path,
                target -> Targets.canAffect(target) && !runtime.dashHit.contains(target.getUuid()) && player.canSee(target))) {
            entity.damage(player.getDamageSources().playerAttack(player), 22 * RiftMod.config().damageMultiplier);
            entity.setVelocity(runtime.dashDirection.multiply(1.4).add(0, .5, 0));
            entity.velocityModified = true;
            runtime.dashHit.add(entity.getUuid());
        }
        runtime.previousPos = player.getPos();
        runtime.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + .7, player.getZ(),
                9, .3, .5, .3, .05);
        boolean collision = player.horizontalCollision || player.isOnGround() && runtime.dashDirection.y < -.25;
        if (--runtime.dashTicks == 0 || collision) {
            runtime.dashTicks = 0;
            Vec3d center = player.getPos().add(0, .5, 0);
            Targets.shockwave(runtime.world, center, player, 5, 22, 1.5, runtime.dashHit);
            RiftEffects.wave(runtime.world, center, 5, AbilityMode.SURGE.color());
            RiftEffects.sound(runtime.world, center, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, .8f, .7f);
            pushPlayer(player, player.getVelocity().multiply(.35));
        } else {
            pushPlayer(player, runtime.dashDirection.multiply(1.65).add(0, .12, 0));
        }
    }

    private static void tickThrown(ServerPlayerEntity player, Runtime runtime) {
        runtime.thrown.removeIf(thrown -> {
            LivingEntity entity = thrown.entity;
            if (entity.isRemoved() || entity.getWorld() != runtime.world) return true;
            thrown.age++;
            if (!entity.isAlive() || thrown.age > 3 && (entity.horizontalCollision || entity.verticalCollision)
                    || thrown.age >= 25) {
                Vec3d center = entity.getPos().add(0, .5, 0);
                Targets.shockwave(runtime.world, center, player, 3, 12, 1.1, Set.of());
                RiftEffects.wave(runtime.world, center, 3, AbilityMode.VECTOR.color());
                RiftEffects.sound(runtime.world, center, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, .6f, .6f);
                return true;
            }
            return false;
        });
    }

    private static void tickSingularity(ServerPlayerEntity player, Runtime runtime) {
        Singularity well = runtime.singularity;
        if (!RiftMod.hasGauntlet(player) || !runtime.world.isChunkLoaded(BlockPos.ofFloored(well.center))) {
            runtime.singularity = null;
            return;
        }
        well.age++;
        for (LivingEntity entity : Targets.nearby(runtime.world, well.center, 9, player)) {
            Vec3d delta = well.center.subtract(entity.getPos().add(0, entity.getHeight() * .5, 0));
            Vec3d velocity = delta.multiply(.12);
            if (velocity.lengthSquared() > .81) velocity = velocity.normalize().multiply(.9);
            entity.setVelocity(velocity.add(0, .08, 0));
            entity.velocityModified = true;
            entity.fallDistance = 0;
            if (well.age % 10 == 0) entity.damage(player.getDamageSources().playerAttack(player),
                    2 * RiftMod.config().damageMultiplier);
        }
        magnet(runtime.world, well.center, 9, .12);
        if (well.age % 4 == 0) RiftEffects.singularity(runtime.world, well.center, well.age, SINGULARITY_DURATION);
        if (well.age == 40) RiftEffects.sound(runtime.world, well.center, SoundEvents.BLOCK_BEACON_POWER_SELECT, .6f, .55f);
        if (well.age >= SINGULARITY_DURATION) {
            Targets.shockwave(runtime.world, well.center, player, 9, 40, 2.1, Set.of());
            RiftEffects.wave(runtime.world, well.center, 9, AbilityMode.SINGULARITY.color());
            runtime.world.spawnParticles(ParticleTypes.END_ROD, well.center.x, well.center.y, well.center.z,
                    55, .8, .8, .8, .3);
            RiftEffects.sound(runtime.world, well.center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, .8f, .65f);
            runtime.singularity = null;
        }
    }

    private static void magnet(ServerWorld world, Vec3d center, double radius, double strength) {
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(center, center).expand(radius),
                item -> item.isAlive() && item.squaredDistanceTo(center) < radius * radius)) {
            Vec3d delta = center.subtract(item.getPos());
            Vec3d velocity = delta.multiply(strength);
            if (velocity.lengthSquared() > .64) velocity = velocity.normalize().multiply(.8);
            item.setVelocity(velocity);
            item.velocityModified = true;
        }
    }

    private static void pushPlayer(ServerPlayerEntity player, Vec3d velocity) {
        player.setVelocity(velocity);
        player.velocityModified = true;
        if (player.networkHandler != null) player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void syncItemMode(ServerPlayerEntity player) {
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).isOf(RiftMod.GAUNTLET)) {
                RiftGauntletItem.setMode(player.getStackInHand(hand), power(player).mode());
            }
        }
    }

    public static void sendState(ServerPlayerEntity player) {
        if (player.networkHandler == null || !ServerPlayNetworking.canSend(player, PowerPayload.ID)) return;
        PowerState state = power(player);
        Runtime runtime = ACTIVE.get(player.getUuid());
        ServerPlayNetworking.send(player, new PowerPayload(state.energy(), PowerState.MAX_ENERGY,
                state.mode().ordinal(), state.cooldown(AbilityMode.VECTOR), state.cooldown(AbilityMode.SURGE),
                state.cooldown(AbilityMode.SINGULARITY), runtime == null ? 0 : runtime.held.size()));
    }

    private static void feedback(ServerPlayerEntity player, String key, Object... args) {
        Runtime runtime = runtime(player);
        int now = player.getServer().getTicks();
        if (now - runtime.lastFeedback < 12) return;
        runtime.lastFeedback = now;
        player.sendMessage(Text.translatable(key, args).formatted(Formatting.GOLD), true);
    }

    private static void cancelHold(Runtime runtime) {
        runtime.held.forEach(entity -> HELD_BY.remove(entity.getUuid()));
        runtime.held.clear();
    }

    public static void cleanup(ServerPlayerEntity player) {
        Runtime runtime = ACTIVE.remove(player.getUuid());
        if (runtime != null) cancelHold(runtime);
    }

    private static final class Runtime {
        final ServerWorld world;
        final List<LivingEntity> held = new ArrayList<>();
        final List<Thrown> thrown = new ArrayList<>();
        final Set<UUID> dashHit = new HashSet<>();
        int lastSwitch = -100;
        int lastFeedback = -100;
        int holdTicks;
        int dashTicks;
        int fallProtection;
        Vec3d dashDirection = Vec3d.ZERO;
        Vec3d previousPos = Vec3d.ZERO;
        Singularity singularity;

        Runtime(ServerWorld world) { this.world = world; }
    }

    private static final class Thrown {
        final LivingEntity entity;
        int age;
        Thrown(LivingEntity entity) { this.entity = entity; }
    }

    private static final class Singularity {
        final Vec3d center;
        int age;
        Singularity(Vec3d center) { this.center = center; }
    }
}
