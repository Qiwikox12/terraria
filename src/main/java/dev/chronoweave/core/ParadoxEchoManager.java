package dev.chronoweave.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks accumulated damage on targets marked with Temporal Instability and detonates
 * a delayed Paradox Echo strike that scales with combo hits.
 */
public final class ParadoxEchoManager {
    public static final class ParadoxMark {
        private final UUID targetId;
        private final UUID attackerId;
        private float accumulatedDamage;
        private int comboHits;
        private long triggerTick;

        public ParadoxMark(UUID targetId, UUID attackerId, float initialDamage, long triggerTick) {
            this.targetId = targetId;
            this.attackerId = attackerId;
            this.accumulatedDamage = Math.max(0.0f, initialDamage);
            this.comboHits = 1;
            this.triggerTick = triggerTick;
        }

        public void recordHit(float damage, long newTriggerTick) {
            this.accumulatedDamage += Math.max(0.0f, damage);
            this.comboHits = Math.min(8, this.comboHits + 1);
            this.triggerTick = Math.max(this.triggerTick, newTriggerTick);
        }

        public float computeEchoDamage() {
            // Base 45% of accumulated damage, scaling up by +15% per extra combo hit (up to 2.0x multiplier)
            float resonanceMultiplier = Math.min(2.0f, 1.0f + (this.comboHits - 1) * 0.15f);
            return this.accumulatedDamage * 0.45f * resonanceMultiplier;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public UUID getAttackerId() {
            return attackerId;
        }

        public int getComboHits() {
            return comboHits;
        }

        public long getTriggerTick() {
            return triggerTick;
        }
    }

    private static final Map<UUID, ParadoxMark> ACTIVE_MARKS = new ConcurrentHashMap<>();

    private ParadoxEchoManager() {
    }

    public static ParadoxMark recordStrike(UUID targetId, UUID attackerId, float damage, long currentTick, int delayTicks) {
        return ACTIVE_MARKS.compute(targetId, (id, existing) -> {
            if (existing == null) {
                return new ParadoxMark(targetId, attackerId, damage, currentTick + delayTicks);
            }
            existing.recordHit(damage, currentTick + Math.min(delayTicks, 15));
            return existing;
        });
    }

    public static ParadoxMark getMark(UUID targetId) {
        return ACTIVE_MARKS.get(targetId);
    }

    public static void clearAll() {
        ACTIVE_MARKS.clear();
    }

    public static void tickServerWorld(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, ParadoxMark>> it = ACTIVE_MARKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ParadoxMark> entry = it.next();
            ParadoxMark mark = entry.getValue();
            Entity entity = world.getEntity(mark.getTargetId());
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            if (!target.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= mark.getTriggerTick()) {
                it.remove();
                detonateEcho(world, target, mark);
            } else if (now % 4 == 0) {
                // Orbiting temporal warning sparks while mark is charging
                double angle = (now * 0.45) % (Math.PI * 2.0);
                double rx = Math.cos(angle) * 0.75;
                double rz = Math.sin(angle) * 0.75;
                world.spawnParticles(ParticleTypes.WITCH,
                        target.getX() + rx, target.getBodyY(0.6), target.getZ() + rz,
                        2, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    public static void detonateEcho(ServerWorld world, LivingEntity target, ParadoxMark mark) {
        float echoDamage = Math.max(2.0f, mark.computeEchoDamage());
        Entity attackerEntity = mark.getAttackerId() != null ? world.getEntity(mark.getAttackerId()) : null;

        // Reset hurt time briefly so the delayed echo always connects cleanly
        target.timeUntilRegen = 0;
        if (attackerEntity instanceof LivingEntity livingAttacker) {
            target.damage(world.getDamageSources().indirectMagic(livingAttacker, livingAttacker), echoDamage);
            // Temporal siphon heals the wielder for 20% of the echo detonation
            livingAttacker.heal(echoDamage * 0.20f);
        } else {
            target.damage(world.getDamageSources().magic(), echoDamage);
        }

        // Cross-slash particle geometry + sonic boom
        world.spawnParticles(ParticleTypes.SONIC_BOOM,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        for (int i = -5; i <= 5; i++) {
            double offset = i * 0.22;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX() + offset, target.getBodyY(0.5) + offset * 0.5, target.getZ() + offset,
                    1, 0.0, 0.0, 0.0, 0.01);
            world.spawnParticles(ParticleTypes.END_ROD,
                    target.getX() + offset, target.getBodyY(0.5) - offset * 0.5, target.getZ() - offset,
                    1, 0.0, 0.0, 0.0, 0.01);
        }
        world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS, 0.65f, 1.55f);
        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
                SoundCategory.PLAYERS, 1.0f, 0.75f);
    }
}
