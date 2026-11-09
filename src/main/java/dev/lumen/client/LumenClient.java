package dev.lumen.client;

import dev.lumen.item.ResonanceMode;
import dev.lumen.item.ResonatorItem;
import dev.lumen.item.LumenComponents;
import dev.lumen.network.LumenNetworking.CycleModePayload;
import dev.lumen.network.LumenNetworking.PulsePayload;
import dev.lumen.network.LumenNetworking.ScanResultPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class LumenClient implements ClientModInitializer {
    private static KeyBinding cycleMode;

    @Override
    public void onInitializeClient() {
        cycleMode = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lumen.cycle_mode",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.lumen.main"));
        ClientPlayNetworking.registerGlobalReceiver(ScanResultPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().world != null) {
                        HighlightStore.accept(payload, context.client().world);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(PulsePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().world != null) {
                        HighlightStore.addPulse(payload, context.client().world);
                    }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(LumenClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HighlightStore.clear());
        HudRenderCallback.EVENT.register((context, tickCounter) -> ResonanceHud.render(context));
        ResonanceRenderer.init();
    }

    private static void tick(MinecraftClient client) {
        HighlightStore.tick(client);
        if (client.player == null || client.currentScreen != null) {
            return;
        }
        while (cycleMode.wasPressed()) {
            ItemStack stack = ResonatorItem.getHeldStack(client.player);
            if (!stack.isEmpty()) {
                ResonanceMode next = ResonatorItem.getMode(stack).next();
                stack.set(LumenComponents.MODE, next.id());
                ClientPlayNetworking.send(new CycleModePayload(next.id()));
            }
        }
    }
}
