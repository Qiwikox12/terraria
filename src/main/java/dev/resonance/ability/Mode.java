package dev.resonance.ability;

public enum Mode {
    WELL("well", 0x54E6D1, 30, 16),
    PULSE("pulse", 0xFFB965, 40, 20),
    STEP("step", 0xADA0FF, 20, 12);

    private final String id;
    private final int color;
    private final int cost;
    private final int cooldown;

    Mode(String id, int color, int cost, int cooldown) {
        this.id = id;
        this.color = color;
        this.cost = cost;
        this.cooldown = cooldown;
    }

    public String translationKey() {
        return "mode.resonance." + id;
    }

    public int color() {
        return color;
    }

    public int cost() {
        return cost;
    }

    public int cooldown() {
        return cooldown;
    }

    public Mode next(int direction) {
        return values()[Math.floorMod(ordinal() + Integer.signum(direction), values().length)];
    }

    public static Mode fromIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : WELL;
    }
}
