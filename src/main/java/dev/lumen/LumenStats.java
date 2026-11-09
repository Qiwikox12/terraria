package dev.lumen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.StatFormatter;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

public final class LumenStats {
    public static final Identifier PULSES = register("pulses");
    public static final Identifier DEEP_SCANS = register("deep_scans");
    public static final Identifier ORE_SCANS = register("ore_scans");
    public static final Identifier CACHE_SCANS = register("cache_scans");
    public static final Identifier LIFE_SCANS = register("life_scans");

    private static Identifier register(String path) {
        Identifier id = Lumen.id(path);
        Registry.register(Registries.CUSTOM_STAT, id, id);
        Stats.CUSTOM.getOrCreateStat(id, StatFormatter.DEFAULT);
        return id;
    }

    public static void increment(ServerPlayerEntity player, Identifier stat) {
        player.incrementStat(Stats.CUSTOM.getOrCreateStat(stat));
    }

    public static void init() {
    }
}
