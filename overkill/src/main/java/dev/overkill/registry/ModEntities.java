package dev.overkill.registry;

import dev.overkill.OverkillMod;
import dev.overkill.entity.SingularityProjectile;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<SingularityProjectile> SINGULARITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			OverkillMod.id("singularity"),
			EntityType.Builder.<SingularityProjectile>of(SingularityProjectile::new, MobCategory.MISC)
					.sized(0.3F, 0.3F)
					.clientTrackingRange(8)
					.updateInterval(10)
					.build("singularity"));

	private ModEntities() {
	}

	public static void init() {
	}
}
