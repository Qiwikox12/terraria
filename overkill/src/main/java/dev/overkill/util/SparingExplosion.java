package dev.overkill.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.jetbrains.annotations.Nullable;

/** Explosion rules for our weapons: never hurts or shoves the wielder or their side, never destroys loot. */
public class SparingExplosion extends ExplosionDamageCalculator {
	@Nullable
	private final Entity wielder;
	private final boolean damageObjects;

	public SparingExplosion(@Nullable Entity wielder, boolean damageObjects) {
		this.wielder = wielder;
		this.damageObjects = damageObjects;
	}

	private boolean spared(Entity entity) {
		if (entity == wielder || entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
			return true;
		}
		if (entity instanceof LivingEntity) {
			return !Targeting.canHarm(wielder, entity, true);
		}
		return !damageObjects;
	}

	@Override
	public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
		return !spared(entity) && super.shouldDamageEntity(explosion, entity);
	}

	@Override
	public float getKnockbackMultiplier(Entity entity) {
		return spared(entity) && !(entity instanceof ItemEntity) ? 0.0F : super.getKnockbackMultiplier(entity);
	}
}
