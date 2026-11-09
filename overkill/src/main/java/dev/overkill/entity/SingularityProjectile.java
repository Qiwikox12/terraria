package dev.overkill.entity;

import dev.overkill.effect.BlackHoleEffect;
import dev.overkill.effect.WorldEffects;
import dev.overkill.registry.ModEntities;
import dev.overkill.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SingularityProjectile extends ThrowableItemProjectile {
	public SingularityProjectile(EntityType<? extends SingularityProjectile> type, Level level) {
		super(type, level);
	}

	public SingularityProjectile(Level level, LivingEntity owner) {
		super(ModEntities.SINGULARITY, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SINGULARITY;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.02;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			for (int i = 0; i < 3; i++) {
				level().addParticle(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.15, getZ(),
						(random.nextDouble() - 0.5) * 0.08, (random.nextDouble() - 0.5) * 0.08, (random.nextDouble() - 0.5) * 0.08);
			}
			level().addParticle(ParticleTypes.SQUID_INK, getX(), getY() + 0.15, getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel level && !isRemoved()) {
			Vec3 pos = result.getLocation();
			if (result instanceof BlockHitResult blockHit) {
				// Float the hole off the surface so it does not spawn half-buried.
				pos = pos.add(Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()).scale(1.5));
			} else {
				pos = pos.add(0.0, 1.0, 0.0);
			}
			WorldEffects.add(level, new BlackHoleEffect(pos, getOwner()));
			discard();
		}
	}
}
