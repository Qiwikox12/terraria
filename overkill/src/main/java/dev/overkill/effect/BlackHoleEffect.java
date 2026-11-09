package dev.overkill.effect;

import dev.overkill.registry.ModDamageTypes;
import dev.overkill.registry.ModGameRules;
import dev.overkill.util.Fx;
import dev.overkill.util.SparingExplosion;
import dev.overkill.util.Targeting;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Eight seconds of everything falling inward, then a collapse. The thrower is immune to the pull. */
public class BlackHoleEffect implements WorldEffect {
	public static final int LIFETIME = 160;
	public static final double PULL_RADIUS = 18.0;
	public static final double CRUSH_RADIUS = 2.5;
	private static final int RIP_RADIUS = 7;
	private static final float CRUSH_DAMAGE = 10.0F;

	private final Vec3 center;
	@Nullable
	private final UUID owner;
	private final Set<UUID> ripped = new HashSet<>();
	private int age;

	public BlackHoleEffect(Vec3 center, @Nullable Entity owner) {
		this.center = center;
		this.owner = owner == null ? null : owner.getUUID();
	}

	@Override
	public boolean tick(ServerLevel level) {
		age++;
		Entity ownerEntity = owner == null ? null : level.getEntity(owner);
		if (age == 1) {
			Fx.sound(level, center, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4.0F, 0.5F);
			Fx.sound(level, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5F, 1.4F);
			Fx.particle(level, ParticleTypes.FLASH, center, 1, 0.0, 0.0);
		}
		pullEntities(level);
		if (age % 10 == 0) {
			crush(level, ownerEntity);
		}
		if (age % 2 == 0 && ModGameRules.griefing(level)) {
			rip(level);
		}
		visuals(level);
		if (age >= LIFETIME) {
			collapse(level, ownerEntity);
			return false;
		}
		return true;
	}

	private void pullEntities(ServerLevel level) {
		double r2 = PULL_RADIUS * PULL_RADIUS;
		for (Entity e : level.getEntities((Entity) null, new AABB(center, center).inflate(PULL_RADIUS), e -> e.isAlive() && !e.isSpectator())) {
			if (owner != null && e.getUUID().equals(owner)) {
				continue;
			}
			if (e instanceof Player player && player.isCreative()) {
				continue;
			}
			if (TimeStopEffect.isFrozen(e)) {
				continue;
			}
			Vec3 to = center.subtract(e.getBoundingBox().getCenter());
			double distSq = to.lengthSqr();
			if (distSq > r2) {
				continue;
			}
			double d = Math.sqrt(distSq);
			if (e instanceof FallingBlockEntity falling && ripped.contains(falling.getUUID()) && d < 1.3) {
				Fx.particle(level, ParticleTypes.SQUID_INK, falling.position(), 6, 0.3, 0.05);
				falling.discard();
				continue;
			}
			Vec3 velocity;
			if (d < 0.8) {
				velocity = to.scale(0.5);
			} else {
				Vec3 dir = to.scale(1.0 / d);
				double strength = 0.08 + 1.2 / (d + 1.5);
				Vec3 swirl = new Vec3(-dir.z, 0.0, dir.x).scale(0.18 * strength);
				velocity = e.getDeltaMovement().scale(0.75).add(dir.scale(strength)).add(swirl);
			}
			e.setDeltaMovement(velocity);
			e.hurtMarked = true;
			e.resetFallDistance();
		}
	}

	private void crush(ServerLevel level, @Nullable Entity ownerEntity) {
		DamageSource source = ModDamageTypes.source(level, ModDamageTypes.SINGULARITY, ownerEntity);
		for (LivingEntity victim : Targeting.around(level, ownerEntity, center, CRUSH_RADIUS, true)) {
			victim.hurt(source, CRUSH_DAMAGE);
		}
	}

	private void rip(ServerLevel level) {
		for (int i = 0; i < 3; i++) {
			Vec3 offset = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian())
					.normalize().scale(2.0 + level.random.nextDouble() * (RIP_RADIUS - 2.0));
			BlockPos pos = BlockPos.containing(center.add(offset));
			BlockState state = level.getBlockState(pos);
			if (!canRip(level, pos, state)) {
				continue;
			}
			FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
			falling.dropItem = false;
			falling.time = 1;
			falling.setNoGravity(true);
			ripped.add(falling.getUUID());
		}
	}

	static boolean canRip(Level level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= 50.0F;
	}

	private void visuals(ServerLevel level) {
		double grow = Math.min(1.0, age / 12.0);
		Fx.particle(level, ParticleTypes.SQUID_INK, center, 5, 0.25 * grow, 0.0);
		Fx.particle(level, ParticleTypes.LARGE_SMOKE, center, 2, 0.2 * grow, 0.0);

		double phase = age * 0.35;
		int points = 26;
		for (int i = 0; i < points; i++) {
			double a = phase + i * Mth.TWO_PI / points;
			double r = (2.0 + 0.7 * Math.sin(i * 1.7 + age * 0.25)) * grow;
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			ParticleOptions type = switch (i % 3) {
				case 0 -> ParticleTypes.END_ROD;
				case 1 -> ParticleTypes.REVERSE_PORTAL;
				default -> ParticleTypes.DRAGON_BREATH;
			};
			// Tilted disk reads much better than a flat ring.
			Fx.particle(level, type, center.x + x, center.y + x * 0.35, center.z + z, 1, 0.0, 0.0);
		}
		for (int i = 0; i < 6; i++) {
			Vec3 dir = new Vec3(level.random.nextGaussian(), level.random.nextGaussian() * 0.5, level.random.nextGaussian()).normalize();
			Fx.moving(level, ParticleTypes.PORTAL, center, dir.scale(4.0 + level.random.nextDouble() * 10.0));
		}
		if (age % 20 == 0) {
			Fx.sound(level, center, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 2.5F, 0.5F);
		}
		if (age % 30 == 15) {
			Fx.sound(level, center, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 4.0F, 0.6F);
		}
	}

	private void collapse(ServerLevel level, @Nullable Entity ownerEntity) {
		boolean grief = ModGameRules.griefing(level);
		DamageSource source = ModDamageTypes.source(level, ModDamageTypes.SINGULARITY, ownerEntity);
		level.explode(ownerEntity, source, new SparingExplosion(ownerEntity, grief), center.x, center.y, center.z, 6.0F, false,
				grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
		Fx.particle(level, ParticleTypes.FLASH, center, 2, 0.2, 0.0);
		Fx.particle(level, ParticleTypes.EXPLOSION_EMITTER, center, 3, 1.0, 0.0);
		Fx.particle(level, ParticleTypes.END_ROD, center, 100, 0.3, 0.7);
		Fx.particle(level, ParticleTypes.REVERSE_PORTAL, center, 150, 0.3, 1.5);
		Fx.sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0F, 0.6F);
		end(level, false);
	}

	@Override
	public void end(ServerLevel level, boolean interrupted) {
		// Anything still orbiting drops back to the ground instead of hanging in the sky forever.
		for (UUID id : ripped) {
			if (level.getEntity(id) instanceof FallingBlockEntity falling && !falling.isRemoved()) {
				falling.setNoGravity(false);
			}
		}
		ripped.clear();
	}
}
