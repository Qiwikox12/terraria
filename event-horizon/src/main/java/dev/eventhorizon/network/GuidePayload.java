package dev.eventhorizon.network;

import dev.eventhorizon.EventHorizon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record GuidePayload() implements CustomPayload {
    public static final Id<GuidePayload> ID = new Id<>(EventHorizon.id("guide"));
    public static final PacketCodec<RegistryByteBuf, GuidePayload> CODEC = PacketCodec.unit(new GuidePayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
