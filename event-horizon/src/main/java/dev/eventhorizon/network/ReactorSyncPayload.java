package dev.eventhorizon.network;

import dev.eventhorizon.EventHorizon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ReactorSyncPayload(int energy, int mode, int resonance, int cooldown, int comboTicks) implements CustomPayload {
    public static final Id<ReactorSyncPayload> ID = new Id<>(EventHorizon.id("reactor"));
    public static final PacketCodec<RegistryByteBuf, ReactorSyncPayload> CODEC = PacketCodec.ofStatic((buf, value) -> {
        buf.writeVarInt(value.energy);
        buf.writeVarInt(value.mode);
        buf.writeVarInt(value.resonance);
        buf.writeVarInt(value.cooldown);
        buf.writeVarInt(value.comboTicks);
    }, buf -> new ReactorSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
