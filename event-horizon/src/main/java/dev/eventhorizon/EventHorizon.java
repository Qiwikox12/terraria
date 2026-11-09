package dev.eventhorizon;

import dev.eventhorizon.config.GravityConfig;
import dev.eventhorizon.item.ModItems;
import dev.eventhorizon.network.ModNetworking;
import dev.eventhorizon.server.GravitySystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventHorizon implements ModInitializer {
    public static final String ID = "eventhorizon";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static GravityConfig CONFIG;

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        CONFIG = GravityConfig.load();
        ModItems.initialize();
        ModNetworking.initialize();
        GravitySystem.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(CommandManager.literal(ID)
                .then(CommandManager.literal("kit").requires(source -> source.hasPermissionLevel(2))
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrThrow();
                        player.giveItemStack(new ItemStack(ModItems.GRAVITY_GAUNTLET));
                        player.giveItemStack(new ItemStack(ModItems.FLUX_CELL, 16));
                        player.giveItemStack(new ItemStack(ModItems.FIELD_MANUAL));
                        context.getSource().sendFeedback(() -> Text.translatable("message.eventhorizon.kit"), false);
                        return 1;
                    }))));
        LOGGER.info("Event Horizon ready. Gravity is a suggestion; your buildings are not.");
    }
}
