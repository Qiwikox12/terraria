package dev.overkill.registry;

import com.mojang.serialization.Codec;
import dev.overkill.OverkillMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	/** Set while the Ring of the Absolute is the reason a player may fly, so we never revoke flight granted by someone else. */
	public static final AttachmentType<Boolean> RING_FLIGHT = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.copyOnDeath()
			.buildAndRegister(OverkillMod.id("ring_flight"));

	private ModAttachments() {
	}

	public static void init() {
	}
}
