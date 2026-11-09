package dev.resonance.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenGuidePayload() implements CustomPayload {
    public static final Id<OpenGuidePayload> ID = new Id<>(Identifier.of("resonance", "open_guide"));
    public static final PacketCodec<RegistryByteBuf, OpenGuidePayload> CODEC = PacketCodec.unit(new OpenGuidePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
