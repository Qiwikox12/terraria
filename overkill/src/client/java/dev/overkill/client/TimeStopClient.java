package dev.overkill.client;

import dev.overkill.OverkillMod;
import dev.overkill.network.TimeStopPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Client view of a time stop: monochrome post-shader with an inverted flash on entry/exit, plus a HUD countdown. */
public final class TimeStopClient {
	private static final ResourceLocation GRAYSCALE = OverkillMod.id("shaders/post/time_stop.json");
	private static final ResourceLocation INVERT = ResourceLocation.withDefaultNamespace("shaders/post/invert.json");
	private static final int FLASH_TICKS = 5;

	private static boolean active;
	private static Vec3 center = Vec3.ZERO;
	private static float radius;
	private static int remaining;
	private static int total;
	private static int sinceStart;
	private static int exitFlash;
	private static boolean wasInside;

	@Nullable
	private static PostChain ownChain;
	@Nullable
	private static ResourceLocation ownId;

	private TimeStopClient() {
	}

	static void onPayload(TimeStopPayload payload) {
		if (payload.active()) {
			if (!active) {
				sinceStart = 0;
				total = payload.remainingTicks();
			}
			active = true;
			center = payload.center();
			radius = payload.radius();
			remaining = payload.remainingTicks();
		} else {
			active = false;
			if (wasInside) {
				exitFlash = FLASH_TICKS;
			}
		}
	}

	static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			reset(client);
			return;
		}
		if (active) {
			sinceStart++;
			remaining--;
			// Lost the "stop" packet (e.g. dimension change): give up shortly after the expected end.
			if (remaining < -40) {
				active = false;
			}
		}
		boolean inside = active && client.player.position().distanceToSqr(center) <= radius * radius;
		ResourceLocation wanted = null;
		if (inside) {
			wanted = sinceStart < FLASH_TICKS ? INVERT : GRAYSCALE;
		} else if (exitFlash > 0) {
			exitFlash--;
			wanted = INVERT;
		}
		wasInside = inside;
		apply(client.gameRenderer, wanted);
	}

	private static void apply(GameRenderer renderer, @Nullable ResourceLocation wanted) {
		if (wanted == null) {
			if (ownChain != null && renderer.currentEffect() == ownChain) {
				renderer.shutdownEffect();
			}
			ownChain = null;
			ownId = null;
			return;
		}
		if (!wanted.equals(ownId) || renderer.currentEffect() != ownChain) {
			renderer.loadEffect(wanted);
			ownChain = renderer.currentEffect();
			ownId = wanted;
		}
	}

	static void reset(Minecraft client) {
		active = false;
		exitFlash = 0;
		wasInside = false;
		if (client.gameRenderer != null) {
			apply(client.gameRenderer, null);
		}
	}

	static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!wasInside || client.options.hideGui) {
			return;
		}
		Font font = client.font;
		int width = graphics.guiWidth();
		float seconds = Math.max(0, remaining) / 20.0F;
		Component title = Component.translatable("hud.overkill.time_stopped");
		Component timer = Component.literal(String.format(java.util.Locale.ROOT, "%.1f", seconds));
		int pulse = (int) (180 + 75 * Math.sin(sinceStart * 0.25));
		int color = (pulse << 16) | (0xF0 << 8) | 0xFF;

		graphics.pose().pushPose();
		graphics.pose().translate(width / 2.0F, 18.0F, 0.0F);
		graphics.pose().scale(1.5F, 1.5F, 1.0F);
		graphics.drawCenteredString(font, title, 0, 0, color);
		graphics.pose().popPose();
		graphics.drawCenteredString(font, timer, width / 2, 34, 0xFFFFFF);

		int barWidth = 120;
		int x0 = width / 2 - barWidth / 2;
		float fraction = total <= 0 ? 0.0F : Mth.clamp(remaining / (float) total, 0.0F, 1.0F);
		graphics.fill(x0 - 1, 45, x0 + barWidth + 1, 49, 0xAA000000);
		graphics.fill(x0, 46, x0 + (int) (barWidth * fraction), 48, 0xFF7FDBFF);
	}
}
