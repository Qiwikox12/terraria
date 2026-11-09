package dev.overkill.util;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Targeting {
	private Targeting() {
	}

	/**
	 * @param indiscriminate weapons of mass destruction hit everything except the wielder's own side;
	 *                       precision weapons only hit hostiles and whoever is fighting the wielder.
	 */
	public static boolean canHarm(@Nullable Entity attacker, Entity target, boolean indiscriminate) {
		if (target == attacker || !target.isAlive() || target.isSpectator() || !(target instanceof LivingEntity living)) {
			return false;
		}
		if (living instanceof ArmorStand stand && stand.isMarker()) {
			return false;
		}
		if (target instanceof Player victim) {
			if (victim.isCreative()) {
				return false;
			}
			return !(attacker instanceof Player p) || p.canHarmPlayer(victim);
		}
		if (attacker != null) {
			if (target instanceof OwnableEntity pet) {
				UUID owner = pet.getOwnerUUID();
				if (owner != null && owner.equals(attacker.getUUID())) {
					return false;
				}
			}
			if (target.isAlliedTo(attacker)) {
				return false;
			}
		}
		if (indiscriminate) {
			return true;
		}
		return target instanceof Enemy || (target instanceof Mob mob && attacker != null && mob.getTarget() == attacker);
	}

	public static List<LivingEntity> around(ServerLevel level, @Nullable Entity attacker, Vec3 center, double radius, boolean indiscriminate) {
		double r2 = radius * radius;
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				e -> canHarm(attacker, e, indiscriminate) && e.getBoundingBox().getCenter().distanceToSqr(center) <= r2);
	}

	public static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lenSq = ab.lengthSqr();
		if (lenSq < 1.0E-6) {
			return p.distanceTo(a);
		}
		double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / lenSq));
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
