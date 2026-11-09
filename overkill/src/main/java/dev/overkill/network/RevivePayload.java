package dev.overkill.network;

import dev.overkill.OverkillMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Plays the totem-style "item activation" animation with the ring instead of a totem. */
public record RevivePayload(ItemStack stack) implements CustomPacketPayload {
	public static final Type<RevivePayload> TYPE = new Type<>(OverkillMod.id("revive"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RevivePayload> CODEC =
			ItemStack.STREAM_CODEC.map(RevivePayload::new, RevivePayload::stack);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
