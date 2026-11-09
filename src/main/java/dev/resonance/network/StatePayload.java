package dev.resonance.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StatePayload(int energy, int mode, int cooldownTicks, int wellTicks) implements CustomPayload {
    public static final Id<StatePayload> ID = new Id<>(Identifier.of("resonance", "state"));
    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StatePayload::energy,
            PacketCodecs.VAR_INT, StatePayload::mode,
            PacketCodecs.VAR_INT, StatePayload::cooldownTicks,
            PacketCodecs.VAR_INT, StatePayload::wellTicks, StatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
