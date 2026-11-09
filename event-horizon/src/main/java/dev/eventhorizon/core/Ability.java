package dev.eventhorizon.core;

import java.util.Locale;

public enum Ability {
    PULL(16, 40, 0x56E7F4),
    PULSE(22, 60, 0xFFB35C),
    BLINK(12, 30, 0xB39DFF),
    SINGULARITY(45, 400, 0xF279CF);

    private final int cost;
    private final int cooldownTicks;
    private final int rgb;

    Ability(int cost, int cooldownTicks, int rgb) {
        this.cost = cost;
        this.cooldownTicks = cooldownTicks;
        this.rgb = rgb;
    }

    public int cost() {
        return cost;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public int rgb() {
        return rgb;
    }

    public String translationKey() {
        return "ability.eventhorizon." + name().toLowerCase(Locale.ROOT);
    }

    public Ability next() {
        Ability[] abilities = values();
        return abilities[(ordinal() + 1) % abilities.length];
    }
}
