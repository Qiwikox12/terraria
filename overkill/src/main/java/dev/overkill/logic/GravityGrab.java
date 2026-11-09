package dev.overkill.logic;

import dev.overkill.effect.TimeStopEffect;
import dev.overkill.registry.ModDamageTypes;
import dev.overkill.registry.ModItems;
import dev.overkill.util.Advancements;
import dev.overkill.util.Fx;
import dev.overkill.util.Targeting;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Telekinesis for the Gravity Gauntlet: grab entities or whole blocks, then throw them as kinetic projectiles. */
public final class GravityGrab {
	public static final double GRAB_RANGE = 24.0;
	public static final double BLOCK_RANGE = 16.0;
	public static final double LAUNCH_SPEED = 3.2;
	public static final double REPULSE_RADIUS = 10.0;
	private static final double HOLD_DISTANCE = 3.5;
	/** Thrown objects fly flat for this long before gravity kicks back in. */
	private static final int FLAT_FLIGHT_TICKS = 10;
	private static final DustParticleOptions TETHER = new DustParticleOptions(new Vector3f(0.35F, 0.7F, 1.0F), 0.9F);

	private static final Map<UUID, Held> HELD = new HashMap<>();
	private static final Map<UUID, Thrown> THROWN = new HashMap<>();

	private record Held(Entity target, boolean hadNoGravity) {
	}

	private static final class Thrown {
		final Entity entity;
		@Nullable
		final UUID thrower;
		final boolean hadNoGravity;
		int age;
		double lastSpeed;

		Thrown(Entity entity, @Nullable UUID thrower, boolean hadNoGravity) {
			this.entity = entity;
			this.thrower = thrower;
			this.hadNoGravity = hadNoGravity;
		}
	}

	private GravityGrab() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GravityGrab::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> drop(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID id : Map.copyOf(HELD).keySet()) {
				drop(id);
			}
			THROWN.values().forEach(t -> restoreGravity(t.entity, t.hadNoGravity));
			THROWN.clear();
		});
	}

	public static boolean isHolding(Player player) {
		return HELD.containsKey(player.getUUID());
	}

	@Nullable
	public static Entity heldBy(Player player) {
		Held held = HELD.get(player.getUUID());
		return held == null ? null : held.target;
	}

	public static boolean grab(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(GRAB_RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, limit, new AABB(eye, limit).inflate(1.5),
				e -> canGrab(player, e), eye.distanceToSqr(limit));
		if (entityHit != null) {
			hold(player, entityHit.getEntity());
			return true;
		}

		if (blockHit.getType() == HitResult.Type.BLOCK && eye.distanceTo(blockHit.getLocation()) <= BLOCK_RANGE) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (canLift(level, player, pos, state)) {
				FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
				falling.time = 1;
				falling.dropItem = true;
				hold(player, falling);
				return true;
			}
		}
		player.displayClientMessage(Component.translatable("message.overkill.nothing_to_grab").withStyle(ChatFormatting.GRAY), true);
		return false;
	}

	private static boolean canGrab(ServerPlayer player, Entity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || e instanceof EnderDragon || e instanceof EnderDragonPart
				|| e.hasPassenger(player) || player.hasPassenger(e)) {
			return false;
		}
		if (e instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other))) {
			return false;
		}
		for (Held held : HELD.values()) {
			if (held.target == e) {
				return false;
			}
		}
		return true;
	}

	private static boolean canLift(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(BlockTags.WITHER_IMMUNE)
				|| state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
				|| state.getDestroySpeed(level, pos) < 0.0F) {
			return false;
		}
		if (!player.mayBuild() || !player.mayInteract(level, pos) || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
			return false;
		}
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, null);
	}

	private static void hold(ServerPlayer player, Entity target) {
		if (target.isPassenger()) {
			target.stopRiding();
		}
		THROWN.remove(target.getUUID());
		HELD.put(player.getUUID(), new Held(target, target.isNoGravity()));
		target.setNoGravity(true);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		ServerLevel level = player.serverLevel();
		Vec3 c = target.getBoundingBox().getCenter();
		Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, c, 20, 0.4, 0.2);
		Fx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
	}

	public static boolean release(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return false;
		}
		restoreGravity(held.target, held.hadNoGravity);
		held.target.setDeltaMovement(player.getDeltaMovement());
		held.target.hurtMarked = true;
		Fx.sound(player.serverLevel(), held.target.position(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
		return true;
	}

	public static boolean launch(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return false;
		}
		Entity target = held.target;
		target.setDeltaMovement(player.getLookAngle().scale(LAUNCH_SPEED));
		target.hurtMarked = true;
		THROWN.put(target.getUUID(), new Thrown(target, player.getUUID(), held.hadNoGravity));

		ServerLevel level = player.serverLevel();
		Fx.particle(level, ParticleTypes.SONIC_BOOM, target.getBoundingBox().getCenter(), 1, 0.0, 0.0);
		Fx.sound(level, target.position(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.8F);
		Fx.sound(level, target.position(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	/** Shockwave around the player: shoves everything away and turns incoming projectiles around. */
	public static boolean repulse(ServerLevel level, ServerPlayer player) {
		Vec3 c = player.position().add(0.0, 1.0, 0.0);
		DamageSource source = ModDamageTypes.source(level, ModDamageTypes.GRAVITY_IMPACT, player);
		for (Entity e : level.getEntities(player, new AABB(c, c).inflate(REPULSE_RADIUS), e -> e.isAlive() && !e.isSpectator())) {
			if (e instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other))) {
				continue;
			}
			if (e instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
				continue;
			}
			if (HELD.values().stream().anyMatch(h -> h.target == e) || TimeStopEffect.isFrozen(e)) {
				continue;
			}
			Vec3 offset = e.getBoundingBox().getCenter().subtract(c);
			double d = offset.length();
			if (d > REPULSE_RADIUS) {
				continue;
			}
			Vec3 dir = d < 1.0E-3 ? player.getLookAngle() : offset.scale(1.0 / d);
			if (e instanceof Projectile projectile) {
				projectile.setOwner(player);
				e.setDeltaMovement(dir.scale(Math.max(1.5, e.getDeltaMovement().length() * 1.3)));
			} else {
				double strength = 3.0 * (1.0 - d / REPULSE_RADIUS) + 0.6;
				e.setDeltaMovement(dir.scale(strength).add(0.0, 0.5 + 0.4 * (1.0 - d / REPULSE_RADIUS), 0.0));
				if (e instanceof LivingEntity living && Targeting.canHarm(player, living, true)) {
					living.hurt(source, 6.0F);
					if (!(e instanceof Player) && strength > 1.5) {
						THROWN.put(e.getUUID(), new Thrown(e, player.getUUID(), e.isNoGravity()));
					}
				}
			}
			e.hurtMarked = true;
		}
		Fx.particle(level, ParticleTypes.SONIC_BOOM, c, 1, 0.0, 0.0);
		for (int r = 2; r <= REPULSE_RADIUS; r += 2) {
			Fx.ring(level, ParticleTypes.POOF, player.position().add(0.0, 0.3, 0.0), r, 8 + r * 4, r);
		}
		Fx.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.3F);
		return true;
	}

	private static boolean holdingGauntlet(Player player) {
		return player.getMainHandItem().is(ModItems.GRAVITY_GAUNTLET) || player.getOffhandItem().is(ModItems.GRAVITY_GAUNTLET);
	}

	private static void drop(UUID playerId) {
		Held held = HELD.remove(playerId);
		if (held != null) {
			restoreGravity(held.target, held.hadNoGravity);
		}
	}

	private static void restoreGravity(Entity entity, boolean hadNoGravity) {
		if (!entity.isRemoved()) {
			entity.setNoGravity(hadNoGravity);
		}
	}

	private static void tick(MinecraftServer server) {
		tickHeld(server);
		tickThrown(server);
	}

	private static void tickHeld(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Held> entry = it.next();
			Held held = entry.getValue();
			Entity target = held.target;
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !holdingGauntlet(player) || !target.isAlive() || target.isRemoved()
					|| target.level() != player.level() || target.distanceToSqr(player) > 48.0 * 48.0) {
				it.remove();
				restoreGravity(target, held.hadNoGravity);
				continue;
			}
			if (TimeStopEffect.isFrozen(target)) {
				continue;
			}
			Vec3 anchor = player.getEyePosition().add(player.getLookAngle().scale(HOLD_DISTANCE + target.getBbWidth()));
			Vec3 desired = anchor.subtract(0.0, target.getBbHeight() / 2.0, 0.0);
			Vec3 velocity = desired.subtract(target.position()).scale(0.45);
			if (velocity.length() > 2.5) {
				velocity = velocity.normalize().scale(2.5);
			}
			target.setDeltaMovement(velocity);
			target.hurtMarked = true;
			target.resetFallDistance();
			if (target instanceof FallingBlockEntity falling) {
				falling.time = 1;
			}
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (server.getTickCount() % 3 == 0) {
				ServerLevel level = player.serverLevel();
				Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.8)).subtract(0.0, 0.3, 0.0);
				Fx.line(level, TETHER, hand, target.getBoundingBox().getCenter(), 0.6, 1, 0.03);
				Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, target.getBoundingBox().getCenter(), 2, 0.3, 0.05);
			}
		}
	}

	private static void tickThrown(MinecraftServer server) {
		Iterator<Thrown> it = THROWN.values().iterator();
		while (it.hasNext()) {
			Thrown t = it.next();
			Entity e = t.entity;
			t.age++;
			if (e.isRemoved() || t.age > 100 || !(e.level() instanceof ServerLevel level)) {
				it.remove();
				restoreGravity(e, t.hadNoGravity);
				continue;
			}
			if (TimeStopEffect.isFrozen(e)) {
				t.age--;
				continue;
			}
			if (t.age == FLAT_FLIGHT_TICKS) {
				restoreGravity(e, t.hadNoGravity);
			}
			ServerPlayer thrower = t.thrower == null ? null : server.getPlayerList().getPlayer(t.thrower);
			Vec3 velocity = e.getDeltaMovement();
			double speed = Math.max(velocity.length(), t.lastSpeed);

			AABB sweep = e.getBoundingBox().expandTowards(velocity).inflate(0.5);
			boolean hitEntity = !level.getEntitiesOfClass(LivingEntity.class, sweep,
					v -> v != e && v != thrower && Targeting.canHarm(thrower, v, true)).isEmpty();
			boolean hitBlock = t.age > 1 && !(e instanceof Player) && (e.horizontalCollision || e.verticalCollision || e.onGround());
			if (hitEntity || hitBlock) {
				it.remove();
				restoreGravity(e, t.hadNoGravity);
				impact(level, e, thrower, speed);
				continue;
			}
			if (t.age > 4 && velocity.length() < 0.25) {
				it.remove();
				restoreGravity(e, t.hadNoGravity);
				continue;
			}
			t.lastSpeed = velocity.length();
			Fx.particle(level, ParticleTypes.CLOUD, e.getBoundingBox().getCenter(), 1, 0.1, 0.0);
		}
	}

	private static void impact(ServerLevel level, Entity projectile, @Nullable ServerPlayer thrower, double speed) {
		if (speed < 0.6) {
			return;
		}
		float damage = (float) Mth.clamp(speed * 9.0, 8.0, 45.0);
		Vec3 c = projectile.getBoundingBox().getCenter();
		DamageSource source = ModDamageTypes.source(level, ModDamageTypes.GRAVITY_IMPACT, thrower);
		int kills = 0;
		for (LivingEntity victim : Targeting.around(level, thrower, c, 3.5, true)) {
			if (victim == projectile) {
				continue;
			}
			boolean wasAlive = victim.isAlive();
			victim.hurt(source, damage);
			Vec3 away = victim.getBoundingBox().getCenter().subtract(c);
			if (away.lengthSqr() > 1.0E-4) {
				victim.setDeltaMovement(victim.getDeltaMovement().add(away.normalize().scale(1.2)).add(0.0, 0.4, 0.0));
				victim.hurtMarked = true;
			}
			if (wasAlive && victim.isDeadOrDying()) {
				kills++;
			}
		}
		if (projectile instanceof LivingEntity living && living != thrower) {
			living.hurt(source, damage);
		}
		if (projectile instanceof PrimedTnt tnt) {
			tnt.setFuse(0);
		}
		projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.2));
		projectile.hurtMarked = true;

		Fx.particle(level, ParticleTypes.EXPLOSION, c, 3, 0.6, 0.0);
		Fx.particle(level, ParticleTypes.CLOUD, c, 25, 0.5, 0.2);
		Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.3F);
		Fx.sound(level, c, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (kills > 0 && thrower != null && projectile instanceof LivingEntity) {
			Advancements.grant(thrower, "yeet");
		}
	}
}
