package dev.overkill.item;

import net.minecraft.network.chat.Component;

public enum DevourerMode {
	SINGLE(0),
	CUBE_3(1),
	CUBE_5(2),
	CUBE_9(4),
	VEIN(0);

	/** Half-size of the cube; the cube is (2r+1) blocks on each side. */
	public final int radius;

	DevourerMode(int radius) {
		this.radius = radius;
	}

	public int size() {
		return radius * 2 + 1;
	}

	public DevourerMode next() {
		DevourerMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static DevourerMode byIndex(int index) {
		DevourerMode[] values = values();
		return index >= 0 && index < values.length ? values[index] : SINGLE;
	}

	public Component displayName() {
		return Component.translatable("mode.overkill.devourer." + name().toLowerCase(java.util.Locale.ROOT));
	}
}
