package dev.resonance.item;

import com.mojang.serialization.Codec;
import dev.resonance.ability.AbilityRules;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ResonanceComponents {
    public static final ComponentType<Integer> ENERGY = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("resonance", "energy"),
            ComponentType.<Integer>builder().codec(Codec.intRange(0, AbilityRules.MAX_ENERGY))
                    .packetCodec(PacketCodecs.VAR_INT).build());
    public static final ComponentType<Integer> MODE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("resonance", "mode"),
            ComponentType.<Integer>builder().codec(Codec.intRange(0, 2))
                    .packetCodec(PacketCodecs.VAR_INT).build());

    private ResonanceComponents() {
    }
}
