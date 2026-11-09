package dev.eventhorizon.client;

import dev.eventhorizon.core.Ability;
import dev.eventhorizon.core.ReactorState;
import dev.eventhorizon.item.ModItems;
import dev.eventhorizon.network.CycleModePayload;
import dev.eventhorizon.network.EffectPayload;
import dev.eventhorizon.network.GuidePayload;
import dev.eventhorizon.network.ReactorSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

public final class EventHorizonClient implements ClientModInitializer {
    public static final ReactorView REACTOR = new ReactorView();
    public static KeyBinding cycleKey;
    public static KeyBinding guideKey;

    @Override
    public void onInitializeClient() {
        cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.eventhorizon.cycle", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R, "key.category.eventhorizon"));
        guideKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.eventhorizon.guide", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.category.eventhorizon"));
        ClientPlayNetworking.registerGlobalReceiver(ReactorSyncPayload.ID, (payload, context) ->
            context.client().execute(() -> REACTOR.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(EffectPayload.ID, (payload, context) ->
            context.client().execute(() -> GravityEffects.render(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(GuidePayload.ID, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new FieldGuideScreen())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> REACTOR.reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> REACTOR.reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            REACTOR.tick();
            while (cycleKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null && ModItems.holdingGauntlet(client.player)
                    && ClientPlayNetworking.canSend(CycleModePayload.ID)) {
                    ClientPlayNetworking.send(new CycleModePayload());
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.6f);
                }
            }
            while (guideKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) client.setScreen(new FieldGuideScreen());
            }
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> ReactorHud.render(context));
    }

    public static final class ReactorView {
        public int energy = 100;
        public Ability mode = Ability.PULL;
        public int resonance;
        public int cooldown;
        public int comboTicks;
        public float smoothEnergy = 100;

        void accept(ReactorSyncPayload payload) {
            energy = Math.clamp(payload.energy(), 0, ReactorState.MAX_ENERGY);
            mode = Ability.values()[Math.clamp(payload.mode(), 0, Ability.values().length - 1)];
            resonance = Math.clamp(payload.resonance(), 0, 3);
            cooldown = Math.clamp(payload.cooldown(), 0, mode.cooldownTicks());
            comboTicks = Math.clamp(payload.comboTicks(), 0, ReactorState.COMBO_WINDOW);
        }

        void tick() {
            cooldown = Math.max(0, cooldown - 1);
            comboTicks = Math.max(0, comboTicks - 1);
            if (comboTicks == 0) resonance = 0;
            smoothEnergy += (energy - smoothEnergy) * 0.35f;
        }

        void reset() {
            energy = 100; smoothEnergy = 100; mode = Ability.PULL;
            resonance = 0; cooldown = 0; comboTicks = 0;
        }
    }
}
