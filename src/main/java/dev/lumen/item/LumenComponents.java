package dev.lumen.item;

import com.mojang.serialization.Codec;
import dev.lumen.Lumen;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class LumenComponents {
    public static final ComponentType<Integer> MODE = register("mode");
    public static final ComponentType<Integer> CHARGE = register("charge");
    public static final ComponentType<Integer> CAPACITY = register("capacity");

    private static ComponentType<Integer> register(String path) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Lumen.id(path),
                ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).cache().build());
    }

    public static void init() {
    }
}
