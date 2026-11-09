package dev.resonance.ability;

public final class AbilityRules {
    public static final int MAX_ENERGY = 120;
    public static final int RECHARGE_INTERVAL = 10;
    public static final int RECHARGE_AMOUNT = 6;
    public static final int WELL_DURATION = 80;
    public static final double WELL_RANGE = 14.0;
    public static final double WELL_RADIUS = 6.0;
    public static final double PULSE_RADIUS = 7.0;
    public static final int MAX_TARGETS = 64;
    public static final int FALL_GUARD_TICKS = 40;

    private AbilityRules() {
    }

    public static int clampEnergy(int energy) {
        return Math.clamp(energy, 0, MAX_ENERGY);
    }

    public static int recharge(int energy) {
        return Math.min(MAX_ENERGY, clampEnergy(energy) + RECHARGE_AMOUNT);
    }

    public static float pulseDamage(boolean detonation, double distance) {
        double falloff = 1.0 - 0.4 * Math.clamp(distance / PULSE_RADIUS, 0.0, 1.0);
        return (float) ((detonation ? 24.0 : 12.0) * falloff);
    }
}
