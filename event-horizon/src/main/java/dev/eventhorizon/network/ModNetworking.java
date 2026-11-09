package dev.eventhorizon.network;

import dev.eventhorizon.server.GravitySystem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(CycleModePayload.ID, CycleModePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReactorSyncPayload.ID, ReactorSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EffectPayload.ID, EffectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GuidePayload.ID, GuidePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CycleModePayload.ID,
            (payload, context) -> context.server().execute(() -> GravitySystem.cycle(context.player())));
    }
}
