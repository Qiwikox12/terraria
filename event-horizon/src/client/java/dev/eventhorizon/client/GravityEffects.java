package dev.eventhorizon.client;

import dev.eventhorizon.core.Ability;
import dev.eventhorizon.network.EffectPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class GravityEffects {
    public static void render(MinecraftClient client, EffectPayload effect) {
        if (client.world == null || client.player == null || !finite(effect.origin()) || !finite(effect.end())
            || client.player.squaredDistanceTo(effect.origin()) > 96 * 96) return;
        ClientWorld world = client.world;
        Vec3d center = effect.origin();
        switch (effect.kind()) {
            case EffectPayload.PULL, EffectPayload.SINGULARITY -> {
                boolean ultimate = effect.kind() == EffectPayload.SINGULARITY;
                int color = ultimate ? Ability.SINGULARITY.rgb() : Ability.PULL.rgb();
                double radius = ultimate ? 2.7 - Math.min(effect.age(), 80) * 0.018 : 2.2;
                int count = ultimate ? 48 : 28;
                for (int i = 0; i < count; i++) {
                    double angle = i * Math.PI * 2 / count + effect.age() * 0.12;
                    Vec3d offset = new Vec3d(Math.cos(angle) * radius, Math.sin(angle * 2) * 0.32, Math.sin(angle) * radius);
                    dust(world, center.add(offset), color, ultimate ? 1.25f : 0.95f, offset.multiply(-0.04));
                    if (ultimate) dust(world, center.add(offset.x * 0.6, Math.sin(angle) * radius, offset.z * 0.5),
                        Ability.BLINK.rgb(), 1.1f, Vec3d.ZERO);
                }
                for (int i = 0; i < 8; i++) {
                    double angle = effect.age() * 0.2 + i * 0.8;
                    double height = i * 0.22 - 0.7;
                    dust(world, center.add(Math.cos(angle) * 0.6, height, Math.sin(angle) * 0.6), 0xDFFBFF, 0.8f, Vec3d.ZERO);
                }
                if (ultimate) world.addParticle(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 0.15, 0.15, 0.15);
            }
            case EffectPayload.PULSE -> {
                for (int ring = 1; ring <= 3; ring++) {
                    for (int i = 0; i < 48; i++) {
                        double angle = i * Math.PI / 24;
                        Vec3d direction = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
                        dust(world, center.add(direction.multiply(ring * 1.5)).add(0, -0.5 + ring * 0.15, 0),
                            Ability.PULSE.rgb(), 1.35f, direction.multiply(0.35));
                    }
                }
                world.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
            }
            case EffectPayload.BLINK -> {
                Vec3d delta = effect.end().subtract(center);
                if (delta.lengthSquared() > 20 * 20) return;
                for (int i = 0; i <= 48; i++) {
                    double fraction = i / 48.0;
                    Vec3d point = center.add(delta.multiply(fraction)).add(0, Math.sin(fraction * Math.PI) * 0.4, 0);
                    dust(world, point, Ability.BLINK.rgb(), 1.15f, Vec3d.ZERO);
                    if (i % 4 == 0) world.addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z, 0, 0.02, 0);
                }
            }
            case EffectPayload.IMPLOSION -> {
                for (int i = 0; i < 100; i++) {
                    double y = 1 - 2 * (i + 0.5) / 100;
                    double radius = Math.sqrt(1 - y * y);
                    double angle = i * 2.399963229728653;
                    Vec3d direction = new Vec3d(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    dust(world, center.add(direction.multiply(3)), Ability.SINGULARITY.rgb(), 1.7f, direction.multiply(-0.4));
                }
                world.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
                world.addParticle(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 0, 0, 0);
            }
            default -> { }
        }
    }

    private static boolean finite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static void dust(ClientWorld world, Vec3d position, int rgb, float size, Vec3d velocity) {
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        world.addParticle(new DustParticleEffect(color, size), position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }
}
