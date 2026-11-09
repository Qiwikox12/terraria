package dev.overkill.util;

import dev.overkill.OverkillMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Challenge advancements use an impossible criterion named "code" and are granted from gameplay logic. */
public final class Advancements {
	private Advancements() {
	}

	public static void grant(ServerPlayer player, String name) {
		AdvancementHolder holder = player.server.getAdvancements().get(OverkillMod.id(name));
		if (holder != null) {
			player.getAdvancements().award(holder, "code");
		}
	}
}
