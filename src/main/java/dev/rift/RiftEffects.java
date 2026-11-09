package dev.rift;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class RiftEffects {
    private RiftEffects() { }

    public static void sound(ServerWorld world, Vec3d center, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, center.x, center.y, center.z, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void ring(ServerWorld world, Vec3d center, double radius, int color, double phase) {
        DustParticleEffect dust = dust(color, 1.1f);
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28 + phase;
            Vec3d point = center.add(Math.cos(angle) * radius, Math.sin(angle * 2 + phase) * .12,
                    Math.sin(angle) * radius);
            world.spawnParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    public static void wave(ServerWorld world, Vec3d center, double radius, int color) {
        for (double scale : new double[]{.35, .7, 1}) ring(world, center.add(0, .15, 0), radius * scale, color, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                32, radius * .3, .5, radius * .3, .12);
    }

    public static void tether(ServerWorld world, Vec3d from, Vec3d to) {
        DustParticleEffect dust = dust(AbilityMode.VECTOR.color(), .8f);
        Vec3d direction = to.subtract(from);
        int steps = Math.min(16, Math.max(3, (int) (direction.length() * 2)));
        for (int i = 0; i <= steps; i++) {
            Vec3d point = from.add(direction.multiply((double) i / steps));
            world.spawnParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    public static void singularity(ServerWorld world, Vec3d center, int age, int duration) {
        double progress = (double) age / duration;
        double radius = 2.4 * Math.min(1, age / 10.0) * (1 - .65 * progress);
        ring(world, center, radius, AbilityMode.SINGULARITY.color(), age * .13);
        DustParticleEffect teal = dust(AbilityMode.VECTOR.color(), 1.25f);
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20 + age * .18;
            world.spawnParticles(teal, center.x + Math.cos(angle) * radius * .65,
                    center.y + Math.sin(angle) * radius, center.z + Math.sin(angle) * radius * .4,
                    1, 0, 0, 0, 0);
        }
        world.spawnParticles(dust(0x19142E, 2), center.x, center.y, center.z, 8, .2, .2, .2, 0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 6, .6, .6, .6, .03);
    }

    private static DustParticleEffect dust(int color, float size) {
        return new DustParticleEffect(new Vector3f((color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f, (color & 255) / 255f), size);
    }
}
