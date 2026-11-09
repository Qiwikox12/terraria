package dev.rift.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CycleModePayload() implements CustomPayload {
    public static final CycleModePayload INSTANCE = new CycleModePayload();
    public static final Id<CycleModePayload> ID = new Id<>(Identifier.of("rift", "cycle"));
    public static final PacketCodec<RegistryByteBuf, CycleModePayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
