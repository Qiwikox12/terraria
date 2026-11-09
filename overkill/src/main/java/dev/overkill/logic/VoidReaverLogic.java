package dev.overkill.logic;

import dev.overkill.registry.ModDamageTypes;
import dev.overkill.util.Fx;
import dev.overkill.util.Targeting;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class VoidReaverLogic {
	public static final double CLEAVE_RADIUS = 5.0;
	public static final float CLEAVE_RATIO = 0.6F;
	public static final float EXECUTE_THRESHOLD = 0.25F;
	public static final double DASH_RANGE = 16.0;
	/** Cleave cannot fire more often than this, so spam-clicking at low attack charge is not rewarded. */
	private static final int CLEAVE_INTERVAL = 8;

	private static final Map<LivingEntity, Long> LAST_CLEAVE = new WeakHashMap<>();

	private VoidReaverLogic() {
	}

	public static void onHit(ServerLevel level, LivingEntity target, LivingEntity attacker) {
		float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		DamageSource rift = ModDamageTypes.source(level, ModDamageTypes.VOID_RIFT, attacker);
		Vec3 origin = target.getBoundingBox().getCenter();

		long now = level.getGameTime();
		Long last = LAST_CLEAVE.get(attacker);
		if (last == null || now - last >= CLEAVE_INTERVAL) {
			LAST_CLEAVE.put(attacker, now);
			for (LivingEntity victim : Targeting.around(level, attacker, origin, CLEAVE_RADIUS, false)) {
				if (victim == target) {
					continue;
				}
				victim.hurt(rift, damage * CLEAVE_RATIO);
				Fx.line(level, ParticleTypes.REVERSE_PORTAL, origin, victim.getBoundingBox().getCenter(), 0.35, 2, 0.05);
			}
			Fx.particle(level, ParticleTypes.SWEEP_ATTACK, origin, 1, 0.0, 0.0);
		}

		if (target.isAlive() && !(target instanceof Player) && target.getHealth() <= target.getMaxHealth() * EXECUTE_THRESHOLD) {
			target.hurt(rift, target.getHealth() + target.getAbsorptionAmount() + 1000.0F);
			Fx.particle(level, ParticleTypes.SOUL, origin, 30, 0.4, 0.15);
			Fx.particle(level, ParticleTypes.SCULK_SOUL, origin, 12, 0.3, 0.1);
			Fx.particle(level, ParticleTypes.REVERSE_PORTAL, origin, 60, 0.2, 0.8);
			Fx.sound(level, origin, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9F, 1.6F);
			Fx.sound(level, origin, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6F, 1.8F);
		}

		attacker.heal(damage * 0.1F);
	}

	/** Teleports up to 16 blocks along the look direction, carving through every enemy on the way. */
	public static boolean voidStep(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(DASH_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double maxDistance = wall.getType() == HitResult.Type.MISS ? DASH_RANGE : Math.max(0.0, eye.distanceTo(wall.getLocation()) - 0.5);

		Vec3 start = player.position();
		Vec3 destination = null;
		for (double d = maxDistance; d >= 1.0; d -= 0.25) {
			Vec3 candidate = start.add(look.scale(d));
			AABB box = player.getBoundingBox().move(candidate.subtract(start));
			if (level.noCollision(player, box)) {
				destination = candidate;
				break;
			}
		}
		if (destination == null) {
			Fx.sound(level, eye, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 0.4F);
			return false;
		}

		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F;
		DamageSource rift = ModDamageTypes.source(level, ModDamageTypes.VOID_RIFT, player);
		Vec3 from = start.add(0.0, 0.9, 0.0);
		Vec3 to = destination.add(0.0, 0.9, 0.0);
		AABB swept = player.getBoundingBox().minmax(player.getBoundingBox().move(destination.subtract(start))).inflate(1.5);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, swept, e -> Targeting.canHarm(player, e, false))) {
			if (Targeting.distanceToSegment(victim.getBoundingBox().getCenter(), from, to) > 2.0) {
				continue;
			}
			victim.hurt(rift, damage);
			victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.6, 0.0));
			victim.hurtMarked = true;
			Fx.particle(level, ParticleTypes.SWEEP_ATTACK, victim.getBoundingBox().getCenter(), 1, 0.0, 0.0);
		}

		Fx.line(level, ParticleTypes.REVERSE_PORTAL, from, to, 0.4, 4, 0.25);
		Fx.line(level, ParticleTypes.DRAGON_BREATH, from, to, 1.0, 1, 0.1);
		Fx.particle(level, ParticleTypes.SONIC_BOOM, from, 1, 0.0, 0.0);
		Fx.sound(level, start, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);

		player.teleportTo(destination.x, destination.y, destination.z);
		player.resetFallDistance();
		player.setDeltaMovement(look.scale(0.35));
		player.hurtMarked = true;
		Fx.sound(level, destination, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}
}
