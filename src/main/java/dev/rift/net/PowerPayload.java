package dev.rift.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PowerPayload(float energy, float maxEnergy, int mode, int vectorCooldown,
                           int surgeCooldown, int singularityCooldown, int heldCount) implements CustomPayload {
    public static final Id<PowerPayload> ID = new Id<>(Identifier.of("rift", "power"));
    public static final PacketCodec<RegistryByteBuf, PowerPayload> CODEC = PacketCodec.of(
            PowerPayload::write, PowerPayload::new);

    private PowerPayload(RegistryByteBuf buffer) {
        this(buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryByteBuf buffer) {
        buffer.writeFloat(energy);
        buffer.writeFloat(maxEnergy);
        buffer.writeVarInt(mode);
        buffer.writeVarInt(vectorCooldown);
        buffer.writeVarInt(surgeCooldown);
        buffer.writeVarInt(singularityCooldown);
        buffer.writeVarInt(heldCount);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
