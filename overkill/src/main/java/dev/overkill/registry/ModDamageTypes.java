package dev.overkill.registry;

import dev.overkill.OverkillMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ModDamageTypes {
	public static final ResourceKey<DamageType> VOID_RIFT = key("void_rift");
	public static final ResourceKey<DamageType> ORBITAL_STRIKE = key("orbital_strike");
	public static final ResourceKey<DamageType> SINGULARITY = key("singularity");
	public static final ResourceKey<DamageType> GRAVITY_IMPACT = key("gravity_impact");
	public static final ResourceKey<DamageType> ABSOLUTE_WRATH = key("absolute_wrath");

	private ModDamageTypes() {
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, OverkillMod.id(name));
	}

	/**
	 * Our death messages always name the attacker, so without one we fall back to vanilla magic damage
	 * instead of rendering a half-filled translation.
	 */
	public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
		if (attacker == null) {
			return level.damageSources().magic();
		}
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
	}
}
