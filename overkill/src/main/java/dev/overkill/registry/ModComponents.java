package dev.overkill.registry;

import com.mojang.serialization.Codec;
import dev.overkill.OverkillMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class ModComponents {
	/** Index into {@link dev.overkill.item.DevourerMode}. */
	public static final DataComponentType<Integer> DEVOURER_MODE = register("devourer_mode", Codec.INT, ByteBufCodecs.VAR_INT);
	public static final DataComponentType<Boolean> AUTO_SMELT = register("auto_smelt", Codec.BOOL, ByteBufCodecs.BOOL);
	/** Orbital designator: strike without destroying terrain. */
	public static final DataComponentType<Boolean> SURGICAL = register("surgical", Codec.BOOL, ByteBufCodecs.BOOL);
	public static final DataComponentType<Boolean> MAGNET = register("magnet", Codec.BOOL, ByteBufCodecs.BOOL);

	private ModComponents() {
	}

	private static <T> DataComponentType<T> register(String name, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, OverkillMod.id(name),
				DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
	}

	public static void init() {
	}
}
