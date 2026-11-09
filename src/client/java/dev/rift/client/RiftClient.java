package dev.rift.client;

import dev.rift.RiftMod;
import dev.rift.net.CycleModePayload;
import dev.rift.net.PowerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class RiftClient implements ClientModInitializer {
    private static KeyBinding cycleModeKey;

    @Override
    public void onInitializeClient() {
        cycleModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rift.cycle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.rift"
        ));

        ClientPlayNetworking.registerGlobalReceiver(PowerPayload.ID, (payload, context) ->
                context.client().execute(() -> RiftHud.accept(payload))
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cycleModeKey.wasPressed()) {
                if (client.player == null || !RiftHud.isGauntletEquipped(client.player)) {
                    continue;
                }

                if (ClientPlayNetworking.canSend(CycleModePayload.ID)) {
                    ClientPlayNetworking.send(CycleModePayload.INSTANCE);
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RiftHud.reset());
        HudRenderCallback.EVENT.register((context, tickCounter) ->
                RiftHud.render(context, cycleModeKey.getBoundKeyLocalizedText())
        );
    }
}
