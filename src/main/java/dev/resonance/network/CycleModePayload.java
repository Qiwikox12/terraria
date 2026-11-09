package dev.resonance.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CycleModePayload(int direction) implements CustomPayload {
    public static final Id<CycleModePayload> ID = new Id<>(Identifier.of("resonance", "cycle_mode"));
    public static final PacketCodec<RegistryByteBuf, CycleModePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, CycleModePayload::direction, CycleModePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
