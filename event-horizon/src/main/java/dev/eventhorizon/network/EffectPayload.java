package dev.eventhorizon.network;

import dev.eventhorizon.EventHorizon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

public record EffectPayload(int kind, Vec3d origin, Vec3d end, int age) implements CustomPayload {
    public static final int PULL = 0, PULSE = 1, BLINK = 2, SINGULARITY = 3, IMPLOSION = 4;
    public static final Id<EffectPayload> ID = new Id<>(EventHorizon.id("effect"));
    public static final PacketCodec<RegistryByteBuf, EffectPayload> CODEC = PacketCodec.ofStatic((buf, value) -> {
        buf.writeVarInt(value.kind);
        buf.writeDouble(value.origin.x); buf.writeDouble(value.origin.y); buf.writeDouble(value.origin.z);
        buf.writeDouble(value.end.x); buf.writeDouble(value.end.y); buf.writeDouble(value.end.z);
        buf.writeVarInt(value.age);
    }, buf -> new EffectPayload(buf.readVarInt(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
        new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
