package dev.eventhorizon.network;

import dev.eventhorizon.EventHorizon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record CycleModePayload() implements CustomPayload {
    public static final Id<CycleModePayload> ID = new Id<>(EventHorizon.id("cycle_mode"));
    public static final PacketCodec<RegistryByteBuf, CycleModePayload> CODEC = PacketCodec.unit(new CycleModePayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
