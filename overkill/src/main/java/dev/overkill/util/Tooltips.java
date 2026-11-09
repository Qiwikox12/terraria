package dev.overkill.util;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class Tooltips {
	/** Replaced by the client entrypoint; common code must not touch client-only Screen. */
	public static BooleanSupplier shiftDown = () -> false;

	private Tooltips() {
	}

	/** Adds the flavour line plus either the detailed ability lines (Shift held) or a hint. */
	public static void describe(List<Component> tooltip, String itemName, int abilityLines) {
		String base = "tooltip.overkill." + itemName;
		tooltip.add(Component.translatable(base + ".flavor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		if (shiftDown.getAsBoolean()) {
			for (int i = 1; i <= abilityLines; i++) {
				tooltip.add(Component.translatable(base + ".line" + i));
			}
		} else {
			tooltip.add(Component.translatable("tooltip.overkill.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public static Component onOff(boolean value) {
		return Component.translatable(value ? "tooltip.overkill.on" : "tooltip.overkill.off")
				.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
	}
}
