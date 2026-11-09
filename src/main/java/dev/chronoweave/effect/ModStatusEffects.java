package dev.chronoweave.effect;

import dev.chronoweave.ChronoWeaveMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class ModStatusEffects {
    public static final RegistryEntry<StatusEffect> TEMPORAL_INSTABILITY = register("temporal_instability",
            new TemporalInstabilityEffect());

    public static final RegistryEntry<StatusEffect> CHRONO_GRACE = register("chrono_grace",
            new ChronoGraceEffect());

    public static final RegistryEntry<StatusEffect> TIME_LOCK = register("time_lock",
            new TimeLockEffect());

    private ModStatusEffects() {
    }

    private static RegistryEntry<StatusEffect> register(String name, StatusEffect effect) {
        return Registry.registerReference(Registries.STATUS_EFFECT, Identifier.of(ChronoWeaveMod.MOD_ID, name), effect);
    }

    public static void init() {
        // Static field initialization registers status effects
    }

    private static final class TemporalInstabilityEffect extends StatusEffect {
        protected TemporalInstabilityEffect() {
            super(StatusEffectCategory.HARMFUL, 0xA832FF, ParticleTypes.PORTAL);
            this.addAttributeModifier(
                    EntityAttributes.GENERIC_ARMOR,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.temporal_instability.armor"),
                    -0.20,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % 10 == 0;
        }

        @Override
        public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
            return true;
        }
    }

    private static final class ChronoGraceEffect extends StatusEffect {
        protected ChronoGraceEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0x00F5D4, ParticleTypes.END_ROD);
            this.addAttributeModifier(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.chrono_grace.speed"),
                    0.25,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            this.addAttributeModifier(
                    EntityAttributes.GENERIC_ATTACK_SPEED,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.chrono_grace.attack_speed"),
                    0.20,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            this.addAttributeModifier(
                    EntityAttributes.PLAYER_BLOCK_BREAK_SPEED,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.chrono_grace.mining_speed"),
                    0.30,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            this.addAttributeModifier(
                    EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.chrono_grace.fall_reduction"),
                    -0.50,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(0.5f * (amplifier + 1));
            }
            return true;
        }
    }

    private static final class TimeLockEffect extends StatusEffect {
        protected TimeLockEffect() {
            super(StatusEffectCategory.HARMFUL, 0x38BDF8, ParticleTypes.ELECTRIC_SPARK);
            this.addAttributeModifier(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.time_lock.speed"),
                    -0.95,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            this.addAttributeModifier(
                    EntityAttributes.GENERIC_JUMP_STRENGTH,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "effect.time_lock.jump"),
                    -0.95,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
            entity.setVelocity(new Vec3d(0.0, Math.min(0.0, entity.getVelocity().y * 0.1), 0.0));
            entity.velocityModified = true;
            return true;
        }
    }
}
