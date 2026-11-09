package dev.overkill.effect;

import dev.overkill.registry.ModDamageTypes;
import dev.overkill.registry.ModGameRules;
import dev.overkill.util.Advancements;
import dev.overkill.util.Fx;
import dev.overkill.util.SparingExplosion;
import dev.overkill.util.Targeting;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Three seconds of targeting laser and beeping, then the sky falls on whatever is under it. */
public class OrbitalStrikeEffect implements WorldEffect {
	public static final int CHARGE_TICKS = 60;
	public static final double KILL_RADIUS = 16.0;
	private static final int SHOCKWAVE_TICKS = 24;
	/** Stop following a locked target shortly before impact so a sprinting player has a sliver of a chance. */
	private static final int TRACKING_GRACE = 6;

	private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1.0F, 0.08F, 0.05F), 2.0F);
	private static final DustParticleOptions RING = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.0F), 1.4F);
	private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.6F, 0.0F, 0.0F), 1.0F);

	private final UUID owner;
	private final boolean surgical;
	private Vec3 target;
	@Nullable
	private Entity tracked;
	private int age;

	public OrbitalStrikeEffect(Entity owner, Vec3 target, @Nullable Entity tracked, boolean surgical) {
		this.owner = owner.getUUID();
		this.target = target;
		this.tracked = tracked;
		this.surgical = surgical;
	}

	public Vec3 target() {
		return target;
	}

	@Override
	public boolean tick(ServerLevel level) {
		age++;
		if (tracked != null) {
			if (tracked.isAlive() && tracked.level() == level && age <= CHARGE_TICKS - TRACKING_GRACE) {
				target = tracked.position();
			} else if (age > CHARGE_TICKS - TRACKING_GRACE || !tracked.isAlive()) {
				tracked = null;
			}
		}
		if (age < CHARGE_TICKS) {
			charge(level);
		} else if (age == CHARGE_TICKS) {
			if (!level.isLoaded(BlockPos.containing(target))) {
				return false;
			}
			impact(level);
		} else {
			shockwave(level, age - CHARGE_TICKS);
		}
		return age < CHARGE_TICKS + SHOCKWAVE_TICKS;
	}

	private void charge(ServerLevel level) {
		float progress = age / (float) CHARGE_TICKS;
		if (age % 2 == 0) {
			double width = 0.03 + progress * 0.2;
			for (double y = target.y; y < target.y + 64.0; y += 1.6) {
				Fx.particle(level, BEAM, target.x, y, target.z, 1, width, 0.0);
			}
			double radius = 1.0 + (1.0 - progress) * 11.0;
			Fx.ring(level, RING, target.add(0.0, 0.2, 0.0), radius, 28, age * 0.09);
		}
		if (age % 6 == 0) {
			Fx.ring(level, EDGE, target.add(0.0, 0.1, 0.0), KILL_RADIUS, 48, -age * 0.02);
		}

		int interval = age < 30 ? 10 : age < 48 ? 5 : 2;
		if (age % interval == 0) {
			float pitch = 0.5F + progress * 1.5F;
			Fx.sound(level, target, SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS, 4.0F, pitch);
			if (level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player && player.position().distanceTo(target) > 40.0) {
				player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, pitch);
			}
		}
		if (age == CHARGE_TICKS - 14) {
			Fx.sound(level, target, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 8.0F, 0.5F);
			Fx.sound(level, target.add(0.0, 30.0, 0.0), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 8.0F, 2.0F);
		}
	}

	private void impact(ServerLevel level) {
		Entity ownerEntity = level.getEntity(owner);
		boolean grief = !surgical && ModGameRules.griefing(level);
		Level.ExplosionInteraction interaction = grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
		DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ORBITAL_STRIKE, ownerEntity);
		SparingExplosion rules = new SparingExplosion(ownerEntity, grief);

		// Snapshot victims before the blast so kills by the explosion itself still count.
		List<LivingEntity> victims = Targeting.around(level, ownerEntity, target, KILL_RADIUS, true);

		for (int i = 0; i < 4; i++) {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			if (bolt != null) {
				bolt.moveTo(target.x + (level.random.nextDouble() - 0.5) * 6.0, target.y, target.z + (level.random.nextDouble() - 0.5) * 6.0);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}

		level.explode(ownerEntity, source, rules, target.x, target.y, target.z, 8.0F, false, interaction);
		if (grief) {
			for (int depth = 3; depth <= 9; depth += 3) {
				level.explode(ownerEntity, source, rules, target.x, target.y - depth, target.z, 5.0F, false, interaction);
			}
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Mth.TWO_PI / 8.0;
			level.explode(ownerEntity, source, rules, target.x + Math.cos(a) * 6.0, target.y, target.z + Math.sin(a) * 6.0, 4.5F, false, interaction);
		}

		for (LivingEntity victim : victims) {
			if (!victim.isAlive()) {
				continue;
			}
			double distance = victim.position().distanceTo(target);
			float damage = (float) Mth.lerp(Math.min(1.0, distance / KILL_RADIUS), 150.0, 25.0);
			victim.invulnerableTime = 0;
			victim.hurt(source, damage);
			Vec3 away = victim.position().subtract(target);
			Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(2.2 * (1.0 - distance / KILL_RADIUS) + 0.3);
			victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, 1.1, push.z));
			victim.hurtMarked = true;
		}

		int kills = 0;
		for (LivingEntity victim : victims) {
			if (victim.isDeadOrDying()) {
				kills++;
			}
		}
		if (kills >= 10 && ownerEntity instanceof ServerPlayer player) {
			Advancements.grant(player, "orbital_massacre");
		}

		Fx.particle(level, ParticleTypes.FLASH, target.x, target.y + 1.0, target.z, 3, 0.5, 0.0);
		Fx.particle(level, ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 1.0, target.z, 8, 4.0, 0.0);
		Fx.particle(level, ParticleTypes.LAVA, target.x, target.y + 1.0, target.z, 60, 3.0, 0.0);
		Fx.particle(level, ParticleTypes.END_ROD, target.x, target.y + 1.0, target.z, 120, 0.5, 1.4);
		for (double y = target.y; y < target.y + 64.0; y += 2.0) {
			Fx.particle(level, ParticleTypes.FIREWORK, target.x, y, target.z, 2, 0.4, 0.05);
		}
		Fx.sound(level, target, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 40.0F, 0.6F);
		Fx.sound(level, target, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 12.0F, 0.5F);
		Fx.sound(level, target, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8.0F, 0.6F);
	}

	private void shockwave(ServerLevel level, int t) {
		double radius = 1.0 + t * 0.85;
		Vec3 ground = target.add(0.0, 0.3, 0.0);
		Fx.ring(level, t % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, ground, radius, 56, t * 0.1);
		if (t % 3 == 0) {
			Fx.particle(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, target.x, target.y + 1.0, target.z, 6, 2.0, 0.02);
		}
	}
}
