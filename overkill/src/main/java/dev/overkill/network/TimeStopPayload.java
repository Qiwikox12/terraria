package dev.overkill.network;

import dev.overkill.OverkillMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Tells clients where a time stop is so players inside the bubble get the monochrome screen and countdown. */
public record TimeStopPayload(boolean active, Vec3 center, float radius, int remainingTicks) implements CustomPacketPayload {
	public static final Type<TimeStopPayload> TYPE = new Type<>(OverkillMod.id("time_stop"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeBoolean(payload.active);
				buf.writeDouble(payload.center.x);
				buf.writeDouble(payload.center.y);
				buf.writeDouble(payload.center.z);
				buf.writeFloat(payload.radius);
				buf.writeVarInt(payload.remainingTicks);
			},
			buf -> new TimeStopPayload(buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
					buf.readFloat(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
