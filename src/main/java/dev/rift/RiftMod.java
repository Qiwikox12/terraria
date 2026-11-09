package dev.rift;

import dev.rift.net.CycleModePayload;
import dev.rift.net.PowerPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class RiftMod implements ModInitializer {
    public static final String MOD_ID = "rift";
    public static final Item GAUNTLET = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "rift_gauntlet"),
            new RiftGauntletItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
    private static RiftConfig config = new RiftConfig();

    public static RiftConfig config() { return config; }

    public static boolean hasGauntlet(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(GAUNTLET) || player.getOffHandStack().isOf(GAUNTLET);
    }

    @Override
    public void onInitialize() {
        config = RiftConfig.load(FabricLoader.getInstance().getConfigDir().resolve("rift.json"));
        PayloadTypeRegistry.playS2C().register(PowerPayload.ID, PowerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CycleModePayload.ID, CycleModePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CycleModePayload.ID, (payload, context) ->
                context.server().execute(() -> RiftController.cycleMode(context.player())));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(GAUNTLET));
        RiftController.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                CommandManager.literal("rift")
                        .executes(context -> {
                            context.getSource().sendFeedback(() -> Text.translatable("guide.rift.title")
                                    .formatted(Formatting.AQUA, Formatting.BOLD), false);
                            for (String key : new String[]{"controls", "vector", "surge", "singularity", "safety", "recipe"}) {
                                context.getSource().sendFeedback(() -> Text.translatable("guide.rift." + key), false);
                            }
                            return 1;
                        })
                        .then(CommandManager.literal("recharge").requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    RiftController.power(player).recharge();
                                    RiftSaveData.get(context.getSource().getServer()).markDirty();
                                    RiftController.sendState(player);
                                    context.getSource().sendFeedback(() -> Text.translatable("message.rift.recharged"), false);
                                    return 1;
                                }))));
    }
}
