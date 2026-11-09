package dev.resonance.ability;

import dev.resonance.Resonance;
import dev.resonance.item.GauntletItem;
import dev.resonance.item.ResonanceComponents;
import dev.resonance.network.StatePayload;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class ResonanceAbilities {
    public static final TagKey<EntityType<?>> GRAVITY_IMMUNE = TagKey.of(RegistryKeys.ENTITY_TYPE,
            Resonance.id("gravity_immune"));
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ResonanceAbilities() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ResonanceAbilities::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SESSIONS.remove(oldPlayer.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && source.isIn(DamageTypeTags.IS_FALL)) {
                Session session = SESSIONS.get(player.getUuid());
                return session == null || !session.dimension.equals(player.getWorld().getRegistryKey())
                        || session.fallGuardUntil <= player.getServerWorld().getTime();
            }
            return true;
        });
    }

    public static boolean cycleMode(ServerPlayerEntity player, int direction) {
        if ((direction != 1 && direction != -1) || !canUse(player)) {
            return false;
        }
        long now = player.getServerWorld().getTime();
        Session session = session(player);
        if (now < session.nextModeAt) {
            return false;
        }
        session.nextModeAt = now + 4;
        ItemStack stack = player.getMainHandStack();
        Mode mode = GauntletItem.getMode(stack).next(direction);
        stack.set(ResonanceComponents.MODE, mode.ordinal());
        player.getServerWorld().playSound(null, player.getBlockPos(), Resonance.SWITCH_SOUND,
                SoundCategory.PLAYERS, 0.35F, 0.9F + mode.ordinal() * 0.12F);
        sync(player, session);
        return true;
    }

    public static boolean cast(ServerPlayerEntity player) {
        if (!canUse(player)) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        Session session = session(player);
        long now = world.getTime();
        if (now < session.cooldownUntil || player.getItemCooldownManager().isCoolingDown(Resonance.GAUNTLET)) {
            return false;
        }
        if (player.hasVehicle() || player.isSleeping()) {
            feedback(player, session, Text.translatable("message.resonance.unavailable"));
            return false;
        }
        ItemStack stack = player.getMainHandStack();
        Mode mode = GauntletItem.getMode(stack);
        if (!player.isCreative() && GauntletItem.getEnergy(stack) < mode.cost()) {
            feedback(player, session, Text.translatable("message.resonance.no_energy", mode.cost()));
            return false;
        }

        boolean activated = switch (mode) {
            case WELL -> createWell(player, session);
            case PULSE -> pulse(player, session);
            case STEP -> step(player, session);
        };
        if (!activated) {
            return false;
        }
        if (!player.isCreative()) {
            GauntletItem.setEnergy(stack, GauntletItem.getEnergy(stack) - mode.cost());
        }
        session.cooldownUntil = now + mode.cooldown();
        player.getItemCooldownManager().set(Resonance.GAUNTLET, mode.cooldown());
        sync(player, session);
        return true;
    }

    private static boolean canUse(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && player.getMainHandStack().isOf(Resonance.GAUNTLET);
    }

    private static Session session(ServerPlayerEntity player) {
        Session session = SESSIONS.computeIfAbsent(player.getUuid(),
                uuid -> new Session(player.getWorld().getRegistryKey()));
        if (!session.dimension.equals(player.getWorld().getRegistryKey())) {
            session.dimension = player.getWorld().getRegistryKey();
            session.well = null;
            session.wave = null;
            session.fallGuardUntil = 0;
            session.lastState = null;
        }
        return session;
    }

    private static boolean createWell(ServerPlayerEntity player, Session session) {
        ServerWorld world = player.getServerWorld();
        HitResult hit = player.raycast(AbilityRules.WELL_RANGE, 1.0F, false);
        Vec3d center = hit.getPos();
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            center = center.add(Vec3d.of(block.getSide().getVector()).multiply(0.65));
        }
        if (!world.getWorldBorder().contains(center) || !world.isInBuildLimit(BlockPos.ofFloored(center))
                || !loadedArea(world, Box.of(center, 1, 1, 1))) {
            feedback(player, session, Text.translatable("message.resonance.no_target"));
            return false;
        }
        session.well = new Well(center, world.getTime());
        ResonanceEffects.well(world, center, 0);
        world.playSound(null, center.x, center.y, center.z, Resonance.WELL_SOUND, SoundCategory.PLAYERS, 0.85F, 1.0F);
        return true;
    }

    private static boolean pulse(ServerPlayerEntity player, Session session) {
        ServerWorld world = player.getServerWorld();
        boolean detonation = session.well != null && world.getTime() - session.well.startedAt < AbilityRules.WELL_DURATION
                && player.squaredDistanceTo(session.well.center) <= 32 * 32;
        Vec3d center = detonation ? session.well.center : player.getPos().add(0, 0.9, 0);
        session.well = null;
        int hits = 0;
        for (Entity entity : targets(world, player, center, AbilityRules.PULSE_RADIUS, false)) {
            LivingEntity target = (LivingEntity) entity;
            double distance = target.getBoundingBox().getCenter().distanceTo(center);
            if (target.damage(world.getDamageSources().playerAttack(player), AbilityRules.pulseDamage(detonation, distance))) {
                Vec3d away = target.getPos().subtract(center).multiply(1, 0, 1);
                if (away.lengthSquared() < 0.001) {
                    away = player.getRotationVec(1).multiply(1, 0, 1);
                }
                target.setVelocity(away.normalize().multiply(detonation ? 1.7 : 1.2).add(0, detonation ? 0.65 : 0.4, 0));
                target.velocityModified = true;
                hits++;
            }
        }
        session.wave = new Wave(center, world.getTime(), detonation);
        ResonanceEffects.pulse(world, center, 0, detonation);
        world.playSound(null, center.x, center.y, center.z, Resonance.PULSE_SOUND,
                SoundCategory.PLAYERS, detonation ? 1.15F : 0.8F, detonation ? 0.8F : 1.1F);
        if (detonation && hits > 0) {
            AdvancementEntry advancement = world.getServer().getAdvancementLoader().get(Resonance.id("combination"));
            if (advancement != null) {
                player.getAdvancementTracker().grantCriterion(advancement, "detonate");
            }
            feedback(player, session, Text.translatable("message.resonance.detonated"));
        }
        return true;
    }

    private static boolean step(ServerPlayerEntity player, Session session) {
        ServerWorld world = player.getServerWorld();
        Optional<Vec3d> destination = findDashDestination(world, player, player.getRotationVec(1));
        if (destination.isEmpty()) {
            feedback(player, session, Text.translatable("message.resonance.blocked"));
            return false;
        }
        Vec3d start = player.getPos();
        Vec3d end = destination.get();
        player.requestTeleport(end.x, end.y, end.z);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0;
        session.fallGuardUntil = world.getTime() + AbilityRules.FALL_GUARD_TICKS;
        ResonanceEffects.step(world, start, end);
        world.playSound(null, end.x, end.y, end.z, Resonance.STEP_SOUND, SoundCategory.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    static Optional<Vec3d> findDashDestination(ServerWorld world, Entity entity, Vec3d direction) {
        if (!Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < 0.001) {
            return Optional.empty();
        }
        Vec3d unit = direction.normalize();
        Vec3d start = entity.getPos();
        Box body = entity.getBoundingBox();
        double distance = DashPath.findDistance(sample -> safeDashBox(world, entity, body.offset(unit.multiply(sample))));
        return distance == 0 ? Optional.empty() : Optional.of(start.add(unit.multiply(distance)));
    }

    private static boolean safeDashBox(ServerWorld world, Entity entity, Box box) {
        if (!world.getWorldBorder().contains(box)
                || box.minY < world.getBottomY() || box.maxY > world.getTopY()
                || !loadedArea(world, box) || !world.isSpaceEmpty(entity, box)) {
            return false;
        }
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
                MathHelper.floor(box.maxX - 1.0E-7), MathHelper.floor(box.maxY - 1.0E-7), MathHelper.floor(box.maxZ - 1.0E-7))) {
            if (world.getFluidState(pos).isIn(FluidTags.LAVA)
                    || world.getBlockState(pos).isOf(Blocks.POWDER_SNOW)
                    || world.getBlockState(pos).isOf(Blocks.FIRE)
                    || world.getBlockState(pos).isOf(Blocks.SOUL_FIRE)) {
                return false;
            }
        }
        return true;
    }

    private static boolean loadedArea(ServerWorld world, Box box) {
        int minX = MathHelper.floor(box.minX) >> 4;
        int maxX = MathHelper.floor(box.maxX - 1.0E-7) >> 4;
        int minZ = MathHelper.floor(box.minZ) >> 4;
        int maxZ = MathHelper.floor(box.maxZ - 1.0E-7) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(ChunkPos.toLong(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isHostileTarget(Entity entity) {
        return entity instanceof MobEntity && entity.isAlive() && !entity.isInvulnerable()
                && entity.getType().getSpawnGroup() == SpawnGroup.MONSTER
                && !entity.getType().isIn(GRAVITY_IMMUNE)
                && !(entity instanceof TameableEntity tameable && tameable.isTamed());
    }

    static boolean hasLineOfSight(ServerWorld world, Entity source, Vec3d origin, Entity target) {
        return world.raycast(new RaycastContext(origin, target.getBoundingBox().getCenter(),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, source)).getType() == HitResult.Type.MISS;
    }

    private static List<Entity> targets(ServerWorld world, ServerPlayerEntity player, Vec3d center, double radius, boolean items) {
        return world.getOtherEntities(player, Box.of(center, radius * 2, radius * 2, radius * 2),
                        entity -> (isHostileTarget(entity) || (items && entity instanceof ItemEntity && entity.isAlive()))
                                && !player.isTeammate(entity)
                                && entity.getBoundingBox().getCenter().squaredDistanceTo(center) <= radius * radius)
                .stream().sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center)))
                .filter(entity -> hasLineOfSight(world, player, center, entity))
                .limit(AbilityRules.MAX_TARGETS).toList();
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean holding = canUse(player);
            if (!holding && !SESSIONS.containsKey(player.getUuid())) {
                continue;
            }
            Session session = session(player);
            ServerWorld world = player.getServerWorld();
            long now = world.getTime();
            if (!player.isAlive() || player.isSpectator()) {
                session.well = null;
                session.wave = null;
                session.fallGuardUntil = 0;
                continue;
            }
            if (holding && now % AbilityRules.RECHARGE_INTERVAL == 0) {
                ItemStack stack = player.getMainHandStack();
                int energy = GauntletItem.getEnergy(stack);
                if (energy < AbilityRules.MAX_ENERGY) {
                    GauntletItem.setEnergy(stack, AbilityRules.recharge(energy));
                }
            }
            if (session.fallGuardUntil > now) {
                player.fallDistance = 0;
            }
            if (session.well != null) {
                Well well = session.well;
                int age = (int) (now - well.startedAt);
                if (age >= AbilityRules.WELL_DURATION || player.squaredDistanceTo(well.center) > 32 * 32
                        || !loadedArea(world, Box.of(well.center, 1, 1, 1))) {
                    session.well = null;
                } else {
                    for (Entity target : targets(world, player, well.center, AbilityRules.WELL_RADIUS, true)) {
                        Vec3d delta = well.center.subtract(target.getBoundingBox().getCenter());
                        double length = delta.length();
                        Vec3d attraction = length < 0.35 ? Vec3d.ZERO : delta.normalize().multiply(Math.min(0.26, 0.08 + length * 0.035));
                        Vec3d velocity = target.getVelocity().multiply(0.62).add(attraction).add(0, 0.035, 0);
                        if (velocity.lengthSquared() > 0.85 * 0.85) {
                            velocity = velocity.normalize().multiply(0.85);
                        }
                        target.setVelocity(velocity);
                        target.velocityModified = true;
                        target.fallDistance = 0;
                    }
                    if (age % 4 == 0) {
                        ResonanceEffects.well(world, well.center, age);
                    }
                }
            }
            if (session.wave != null) {
                int age = (int) (now - session.wave.startedAt);
                if (age > 8) {
                    session.wave = null;
                } else if (age > 0 && age % 2 == 0) {
                    ResonanceEffects.pulse(world, session.wave.center, age, session.wave.detonation);
                }
            }
            if (holding && now % 5 == 0) {
                sync(player, session);
            }
        }
    }

    private static void sync(ServerPlayerEntity player, Session session) {
        if (player.networkHandler == null || !ServerPlayNetworking.canSend(player, StatePayload.ID)) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        long now = player.getServerWorld().getTime();
        int remaining = session.well == null ? 0 : Math.max(0, AbilityRules.WELL_DURATION - (int) (now - session.well.startedAt));
        StatePayload state = new StatePayload(GauntletItem.getEnergy(stack), GauntletItem.getMode(stack).ordinal(),
                (int) Math.max(0, session.cooldownUntil - now), remaining);
        if (!state.equals(session.lastState)) {
            ServerPlayNetworking.send(player, state);
            session.lastState = state;
        }
    }

    private static void feedback(ServerPlayerEntity player, Session session, Text message) {
        long now = player.getServerWorld().getTime();
        if (player.networkHandler != null && now >= session.nextFeedbackAt) {
            player.sendMessage(message, true);
            session.nextFeedbackAt = now + 10;
        }
    }

    private static final class Session {
        private RegistryKey<World> dimension;
        private long cooldownUntil;
        private long nextModeAt;
        private long nextFeedbackAt;
        private long fallGuardUntil;
        private Well well;
        private Wave wave;
        private StatePayload lastState;

        private Session(RegistryKey<World> dimension) {
            this.dimension = dimension;
        }
    }

    private record Well(Vec3d center, long startedAt) {
    }

    private record Wave(Vec3d center, long startedAt, boolean detonation) {
    }
}
