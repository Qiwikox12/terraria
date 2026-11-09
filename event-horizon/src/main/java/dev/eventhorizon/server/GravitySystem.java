package dev.eventhorizon.server;

import dev.eventhorizon.EventHorizon;
import dev.eventhorizon.core.Ability;
import dev.eventhorizon.core.ReactorState;
import dev.eventhorizon.item.ModItems;
import dev.eventhorizon.network.EffectPayload;
import dev.eventhorizon.network.ReactorSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GravitySystem {
    private static final int MAX_FIELDS = 32;
    private static final List<Field> FIELDS = new ArrayList<>();
    private static final Map<UUID, Long> NEXT_CYCLE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FEEDBACK = new HashMap<>();

    private record Field(UUID owner, ServerWorld world, Vec3d center, boolean ultimate, double power, long startedAt) {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(GravitySystem::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            FIELDS.removeIf(field -> field.owner.equals(id));
            NEXT_CYCLE.remove(id);
            NEXT_FEEDBACK.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FIELDS.clear();
            NEXT_CYCLE.clear();
            NEXT_FEEDBACK.clear();
        });
    }

    public static long now(ServerPlayerEntity player) { return player.getServer().getOverworld().getTime(); }

    public static ReactorState reactor(ServerPlayerEntity player) {
        return ReactorStore.get(player.getServer()).reactor(player.getUuid());
    }

    private static boolean usable(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.hasVehicle();
    }

    public static void cycle(ServerPlayerEntity player) {
        long time = now(player);
        if (!usable(player) || !ModItems.holdingGauntlet(player) || time < NEXT_CYCLE.getOrDefault(player.getUuid(), 0L)) return;
        NEXT_CYCLE.put(player.getUuid(), time + 3);
        reactor(player).cycleMode();
        ReactorStore.get(player.getServer()).markDirty();
        sync(player);
    }

    public static boolean recharge(ServerPlayerEntity player) {
        if (!usable(player)) return false;
        int added = reactor(player).addEnergy(60);
        if (added == 0) {
            feedback(player, "full");
            return false;
        }
        ReactorStore.get(player.getServer()).markDirty();
        player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
        feedback(player, "recharged");
        sync(player);
        return true;
    }

    public static boolean cast(ServerPlayerEntity player) {
        if (!usable(player) || !ModItems.holdingGauntlet(player)) return false;
        ReactorState state = reactor(player);
        Ability ability = state.mode();
        long time = now(player);
        ReactorState.Failure failure = state.check(ability, time);
        if (failure != ReactorState.Failure.NONE) {
            feedback(player, switch (failure) {
                case ENERGY -> "energy";
                case RESONANCE -> "resonance";
                default -> "cooldown";
            });
            sync(player);
            return false;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getPos().add(0, 0.8, 0);
        if (ability == Ability.BLINK) {
            var destination = GravityTargeting.blinkDestination(player);
            if (destination.isEmpty()) {
                feedback(player, "blocked");
                return false;
            }
            Vec3d end = destination.get();
            player.teleport(world, end.x, end.y, end.z, player.getYaw(), player.getPitch());
            player.fallDistance = 0;
            player.setVelocity(Vec3d.ZERO);
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
            state.commit(ability, time);
            effect(world, EffectPayload.BLINK, origin, end.add(0, 0.8, 0), 0);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.65f, 1.5f);
        } else {
            Vec3d center = ability == Ability.PULSE ? origin : GravityTargeting.aim(player);
            if (!world.getWorldBorder().contains(center) || !world.isChunkLoaded(BlockPos.ofFloored(center))) {
                feedback(player, "blocked");
                return false;
            }
            double radius = ability == Ability.PULL ? 6 : 8;
            List<Entity> targets = GravityTargeting.targets(player, center, radius, ability != Ability.PULSE);
            if (targets.isEmpty()) {
                feedback(player, "no_target");
                return false;
            }
            if (ability != Ability.PULSE && (FIELDS.size() >= MAX_FIELDS || ability == Ability.SINGULARITY
                && FIELDS.stream().anyMatch(field -> field.ultimate && field.owner.equals(player.getUuid())))) {
                feedback(player, "busy");
                return false;
            }
            double power = state.commit(ability, time);
            if (ability == Ability.PULSE) {
                for (Entity target : targets) {
                    damage(player, target, (float) (10 * power));
                    Vec3d away = target.getPos().subtract(player.getPos());
                    if (away.horizontalLengthSquared() < 0.001) away = player.getRotationVec(1);
                    Vec3d horizontal = new Vec3d(away.x, 0, away.z).normalize().multiply(1.5 * power);
                    push(target, horizontal.add(0, 0.5, 0));
                }
                effect(world, EffectPayload.PULSE, center, center, 0);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.65f, 1.6f);
            } else {
                FIELDS.add(new Field(player.getUuid(), world, center, ability == Ability.SINGULARITY, power, time));
                effect(world, ability == Ability.PULL ? EffectPayload.PULL : EffectPayload.SINGULARITY, center, center, 0);
                world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.PLAYERS, 0.65f, ability == Ability.PULL ? 1.4f : 0.6f);
            }
        }
        player.swingHand(player.getMainHandStack().isOf(ModItems.GRAVITY_GAUNTLET)
            ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND, true);
        ReactorStore.get(player.getServer()).markDirty();
        sync(player);
        return true;
    }

    private static void tick(MinecraftServer server) {
        long time = server.getOverworld().getTime();
        ReactorStore store = ReactorStore.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean holding = ModItems.holdingGauntlet(player) && usable(player);
            ReactorState state = holding ? store.reactor(player.getUuid()) : store.find(player.getUuid());
            if (state != null && state.tick(time, holding, EventHorizon.CONFIG.regenerationIntervalTicks)) store.markDirty();
            if (holding && time % 5 == 0) sync(player);
        }
        var iterator = FIELDS.iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(field.owner);
            if (owner == null || !usable(owner) || owner.getServerWorld() != field.world
                || owner.squaredDistanceTo(field.center) > 4096 || !field.world.isChunkLoaded(BlockPos.ofFloored(field.center))) {
                iterator.remove();
                continue;
            }
            int age = (int) (time - field.startedAt);
            int duration = field.ultimate ? 80 : 30;
            if (age >= duration) {
                if (field.ultimate) implode(owner, field);
                iterator.remove();
                continue;
            }
            if (age % 2 == 0) {
                for (Entity target : GravityTargeting.targets(owner, field.center, field.ultimate ? 8 : 6, true)) {
                    Vec3d delta = field.center.add(0, 0.4, 0).subtract(target.getPos().add(0, target.getHeight() * 0.3, 0));
                    double force = Math.min(delta.length() * 0.09, field.ultimate ? 0.5 : 0.38);
                    Vec3d velocity = target.getVelocity().multiply(0.65).add(delta.normalize().multiply(force)).add(0, 0.035, 0);
                    push(target, velocity);
                    if (target instanceof LivingEntity living) {
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, true, false));
                    }
                }
            }
            if (age % 4 == 0) effect(field.world, field.ultimate ? EffectPayload.SINGULARITY : EffectPayload.PULL,
                field.center, field.center, age);
        }
    }

    private static void implode(ServerPlayerEntity owner, Field field) {
        for (Entity target : GravityTargeting.targets(owner, field.center, 8, false)) {
            double falloff = 1 - Math.min(target.getPos().distanceTo(field.center) / 16, 0.5);
            damage(owner, target, (float) (24 * field.power * falloff));
            Vec3d away = target.getPos().subtract(field.center).normalize();
            push(target, away.multiply(0.75).add(0, 0.55, 0));
        }
        effect(field.world, EffectPayload.IMPLOSION, field.center, field.center, 0);
        field.world.playSound(null, BlockPos.ofFloored(field.center), SoundEvents.ENTITY_GENERIC_EXPLODE,
            SoundCategory.PLAYERS, 1.1f, 0.7f);
    }

    private static void damage(ServerPlayerEntity player, Entity target, float amount) {
        if (target instanceof LivingEntity living) living.damage(player.getDamageSources().playerAttack(player),
            (float) (amount * EventHorizon.CONFIG.damageMultiplier));
    }

    private static void push(Entity entity, Vec3d velocity) {
        double resistance = entity instanceof LivingEntity living
            ? Math.clamp(living.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), 0, 1) : 0;
        entity.setVelocity(velocity.multiply(1 - resistance));
        entity.velocityModified = true;
        if (entity instanceof ServerPlayerEntity player) player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void feedback(ServerPlayerEntity player, String key) {
        long time = now(player);
        if (time >= NEXT_FEEDBACK.getOrDefault(player.getUuid(), 0L)) {
            player.sendMessage(Text.translatable("message.eventhorizon." + key), true);
            NEXT_FEEDBACK.put(player.getUuid(), time + 10);
        }
    }

    public static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ReactorSyncPayload.ID)) return;
        ReactorState state = reactor(player);
        long time = now(player);
        int cooldown = Math.max(state.cooldownRemaining(state.mode(), time), state.globalCooldownRemaining(time));
        int comboTicks = (int) Math.clamp(state.snapshot().comboExpiresAt() - time, 0, ReactorState.COMBO_WINDOW);
        ServerPlayNetworking.send(player, new ReactorSyncPayload(state.energy(), state.mode().ordinal(), state.resonance(time), cooldown, comboTicks));
    }

    private static void effect(ServerWorld world, int kind, Vec3d origin, Vec3d end, int age) {
        if (!EventHorizon.CONFIG.showParticles) return;
        EffectPayload payload = new EffectPayload(kind, origin, end, age);
        for (ServerPlayerEntity player : PlayerLookup.around(world, origin, 64)) {
            if (ServerPlayNetworking.canSend(player, EffectPayload.ID)) ServerPlayNetworking.send(player, payload);
        }
    }
}
