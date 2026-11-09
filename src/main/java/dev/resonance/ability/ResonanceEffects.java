package dev.resonance.ability;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

final class ResonanceEffects {
    private static final DustParticleEffect CYAN = dust(Mode.WELL.color(), 1.1F);
    private static final DustParticleEffect AMBER = dust(Mode.PULSE.color(), 1.2F);
    private static final DustParticleEffect VIOLET = dust(Mode.STEP.color(), 1.0F);

    private ResonanceEffects() {
    }

    private static DustParticleEffect dust(int color, float size) {
        return new DustParticleEffect(new Vector3f(
                ((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F), size);
    }

    static void well(ServerWorld world, Vec3d center, int age) {
        double rotation = age * 0.14;
        double radius = 2.6 + Math.sin(age * 0.15) * 0.3;
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24 + rotation;
            Vec3d point = center.add(Math.cos(angle) * radius,
                    Math.sin(angle * 2 + rotation) * 0.45, Math.sin(angle) * radius);
            particle(world, CYAN, point);
            if (i % 4 == 0) {
                particle(world, VIOLET, center.add(Math.cos(angle) * 0.8,
                        (i / 24.0) * 2.2 - 0.8, Math.sin(angle) * 0.8));
            }
        }
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                6, 0.35, 0.5, 0.35, 0.035);
    }

    static void pulse(ServerWorld world, Vec3d center, int age, boolean detonation) {
        double progress = (age + 1) / 9.0;
        double radius = AbilityRules.PULSE_RADIUS * Math.sqrt(progress);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2 * i / 40;
            Vec3d point = center.add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            particle(world, detonation && i % 2 == 0 ? CYAN : AMBER, point);
        }
        if (age == 0) {
            world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                    detonation ? 3 : 1, 0.3, 0.3, 0.3, 0);
            world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                    detonation ? 30 : 16, 0.5, 0.5, 0.5, 0.18);
        }
    }

    static void step(ServerWorld world, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        int samples = Math.max(1, (int) (delta.length() * 3));
        for (int i = 0; i <= samples; i++) {
            Vec3d point = start.add(delta.multiply((double) i / samples)).add(0, 0.9, 0);
            particle(world, VIOLET, point);
            if (i % 4 == 0) {
                world.spawnParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.1, 0.15, 0.1, 0);
            }
        }
        world.spawnParticles(ParticleTypes.PORTAL, end.x, end.y + 0.9, end.z, 18, 0.3, 0.6, 0.3, 0.1);
    }

    private static void particle(ServerWorld world, DustParticleEffect effect, Vec3d position) {
        world.spawnParticles(effect, position.x, position.y, position.z, 1, 0.015, 0.015, 0.015, 0);
    }
}
