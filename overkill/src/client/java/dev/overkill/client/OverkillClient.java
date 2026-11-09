package dev.overkill.client;

import dev.overkill.network.RevivePayload;
import dev.overkill.network.TimeStopPayload;
import dev.overkill.registry.ModEntities;
import dev.overkill.util.Tooltips;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class OverkillClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Tooltips.shiftDown = Screen::hasShiftDown;

		EntityRendererRegistry.register(ModEntities.SINGULARITY, context -> new ThrownItemRenderer<>(context, 1.6F, true));

		ClientPlayNetworking.registerGlobalReceiver(TimeStopPayload.TYPE, (payload, context) -> TimeStopClient.onPayload(payload));
		ClientPlayNetworking.registerGlobalReceiver(RevivePayload.TYPE,
				(payload, context) -> context.client().gameRenderer.displayItemActivation(payload.stack()));

		ClientTickEvents.END_CLIENT_TICK.register(TimeStopClient::tick);
		HudRenderCallback.EVENT.register(TimeStopClient::renderHud);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TimeStopClient.reset(client));
	}
}
