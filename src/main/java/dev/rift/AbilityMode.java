package dev.rift;

public enum AbilityMode {
    VECTOR(20, 50, 0x62EAD5),
    SURGE(30, 40, 0x9DD6FF),
    SINGULARITY(80, 240, 0xEDC875);

    private final int cost;
    private final int cooldown;
    private final int color;

    AbilityMode(int cost, int cooldown, int color) {
        this.cost = cost;
        this.cooldown = cooldown;
        this.color = color;
    }

    public int cost() { return cost; }
    public int cooldown() { return cooldown; }
    public int color() { return color; }
    public String translationKey() { return "mode.rift." + name().toLowerCase(java.util.Locale.ROOT); }
    public AbilityMode next() { return values()[(ordinal() + 1) % values().length]; }

    public static AbilityMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : VECTOR;
    }
}
