package dev.resonance.client;

import dev.resonance.Resonance;
import dev.resonance.ability.Mode;
import dev.resonance.item.GauntletItem;
import dev.resonance.network.CycleModePayload;
import dev.resonance.network.OpenGuidePayload;
import dev.resonance.network.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ResonanceClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.categories.resonance";
    private static KeyBinding cycleModeKey;
    private static KeyBinding openGuideKey;
    private static volatile ServerState latestState;

    @Override
    public void onInitializeClient() {
        cycleModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.resonance.cycle_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY));
        openGuideKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.resonance.guide", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY));

        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, context) -> {
            int energy = Math.max(0, Math.min(payload.energy(), GauntletItem.MAX_ENERGY));
            int mode = Math.max(0, Math.min(payload.mode(), Mode.values().length - 1));
            ServerState state = new ServerState(energy, mode,
                    Math.max(0, payload.cooldownTicks()), Math.max(0, payload.wellTicks()));
            context.client().execute(() -> latestState = state);
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenGuidePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().player != null) {
                        context.client().setScreen(new ResonanceGuideScreen());
                    }
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> latestState = null);
        ClientTickEvents.END_CLIENT_TICK.register(ResonanceClient::tick);
        HudRenderCallback.EVENT.register(ResonanceHud::render);
    }

    static ServerState latestState() {
        return latestState;
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            latestState = null;
            while (cycleModeKey.wasPressed()) {
            }
            while (openGuideKey.wasPressed()) {
            }
            return;
        }

        while (cycleModeKey.wasPressed()) {
            if (client.currentScreen == null && hasGauntlet(client)
                    && client.getNetworkHandler() != null
                    && ClientPlayNetworking.canSend(CycleModePayload.ID)) {
                int direction = client.player.isSneaking() ? -1 : 1;
                ClientPlayNetworking.send(new CycleModePayload(direction));
            }
        }

        while (openGuideKey.wasPressed()) {
            if (client.currentScreen == null && hasGauntlet(client)) {
                client.setScreen(new ResonanceGuideScreen());
            }
        }
    }

    private static boolean hasGauntlet(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().isOf(Resonance.GAUNTLET);
    }

    record ServerState(int energy, int mode, int cooldownTicks, int wellTicks) {
    }
}
