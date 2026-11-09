package dev.chronoweave.registry;

import com.mojang.serialization.Codec;
import dev.chronoweave.ChronoWeaveMod;
import net.minecraft.component.ComponentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.UnaryOperator;

public final class ModDataComponents {
    public static final ComponentType<Integer> CHRONON_CHARGE = register("chronon_charge",
            builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.INTEGER));

    public static final ComponentType<Integer> ARTIFACT_MODE = register("artifact_mode",
            builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.INTEGER));

    public static final ComponentType<Integer> ACTIVE_WAYPOINT_SLOT = register("active_waypoint_slot",
            builder -> builder.codec(Codec.INT).packetCodec(PacketCodecs.INTEGER));

    public static final ComponentType<List<String>> SPATIAL_WAYPOINTS = register("spatial_waypoints",
            builder -> builder.codec(Codec.STRING.listOf()).packetCodec(PacketCodecs.STRING.collect(PacketCodecs.toList())));

    public static final ComponentType<NbtCompound> CAPTURED_ENTITY = register("captured_entity",
            builder -> builder.codec(NbtCompound.CODEC).packetCodec(PacketCodecs.NBT_COMPOUND));

    public static final ComponentType<String> ANCHOR_BINDING = register("anchor_binding",
            builder -> builder.codec(Codec.STRING).packetCodec(PacketCodecs.STRING));

    private ModDataComponents() {
    }

    private static <T> ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(ChronoWeaveMod.MOD_ID, name),
                builderOperator.apply(ComponentType.builder()).build()
        );
    }

    public static void init() {
        // Static field initialization registers all data component types
    }
}
