package dev.lumen.item;

import net.minecraft.text.Text;

public enum ResonanceMode {
    ORE(0, "ore", 0x61E6D2),
    CACHE(1, "cache", 0xF2B866),
    LIFE(2, "life", 0xEF7E9C);

    private static final ResonanceMode[] VALUES = values();
    private final int id;
    private final String path;
    private final int color;

    ResonanceMode(int id, String path, int color) {
        this.id = id;
        this.path = path;
        this.color = color;
    }

    public int id() {
        return id;
    }

    public int color() {
        return color;
    }

    public Text name() {
        return Text.translatable("mode.lumen." + path);
    }

    public ResonanceMode next() {
        return VALUES[(id + 1) % VALUES.length];
    }

    public static ResonanceMode fromId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ORE;
    }
}
