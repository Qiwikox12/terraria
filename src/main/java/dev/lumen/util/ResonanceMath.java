package dev.lumen.util;

public final class ResonanceMath {
    private ResonanceMath() {
    }

    public static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static boolean insideSphere(int x, int y, int z, int radius) {
        long radiusSquared = (long) radius * radius;
        return (long) x * x + (long) y * y + (long) z * z <= radiusSquared;
    }

    public static float signalAlpha(long age, int duration) {
        if (age < 0 || age >= duration) {
            return 0.0f;
        }
        int fadeTicks = Math.min(40, duration / 2);
        return age <= duration - fadeTicks ? 1.0f : (float) (duration - age) / fadeTicks;
    }

    public static float pulseProgress(long age, int duration) {
        float linear = clamp((float) age / duration, 0.0f, 1.0f);
        return 1.0f - (1.0f - linear) * (1.0f - linear);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
