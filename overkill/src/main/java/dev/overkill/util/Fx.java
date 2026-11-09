package dev.overkill.util;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Server-side particle and sound helpers. Particles use the long-range limiter so big effects read from afar. */
public final class Fx {
	private Fx() {
	}

	public static void particle(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double spread, double speed) {
		particle(level, type, x, y, z, count, spread, spread, spread, speed);
	}

	public static void particle(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
			double dx, double dy, double dz, double speed) {
		for (ServerPlayer player : level.players()) {
			level.sendParticles(player, type, true, x, y, z, count, dx, dy, dz, speed);
		}
	}

	public static void particle(ServerLevel level, ParticleOptions type, Vec3 pos, int count, double spread, double speed) {
		particle(level, type, pos.x, pos.y, pos.z, count, spread, speed);
	}

	/** A single particle with an explicit velocity (count 0 makes the client treat the offset as motion). */
	public static void moving(ServerLevel level, ParticleOptions type, Vec3 pos, Vec3 velocity) {
		particle(level, type, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
	}

	public static void line(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 to, double step, int perPoint, double spread) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-4) {
			return;
		}
		Vec3 dir = delta.scale(1.0 / length);
		for (double d = 0; d <= length; d += step) {
			Vec3 p = from.add(dir.scale(d));
			particle(level, type, p.x, p.y, p.z, perPoint, spread, 0.0);
		}
	}

	public static void ring(ServerLevel level, ParticleOptions type, Vec3 center, double radius, int points, double phase) {
		for (int i = 0; i < points; i++) {
			double a = phase + i * Mth.TWO_PI / points;
			particle(level, type, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0, 0.0);
		}
	}

	public static void sphere(ServerLevel level, ParticleOptions type, Vec3 center, double radius, int points) {
		// Fibonacci sphere: evenly spread points without clumping at the poles.
		double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		for (int i = 0; i < points; i++) {
			double y = 1.0 - (i / (double) (points - 1)) * 2.0;
			double r = Math.sqrt(1.0 - y * y);
			double theta = golden * i;
			particle(level, type, center.x + Math.cos(theta) * r * radius, center.y + y * radius, center.z + Math.sin(theta) * r * radius, 1, 0.0, 0.0);
		}
	}

	public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, source, volume, pitch);
	}

	public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
		sound(level, pos, sound.value(), source, volume, pitch);
	}
}
