package dev.chronoweave;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.chronoweave.block.entity.ChronoAnchorBlockEntity;
import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.core.ParadoxEchoManager;
import dev.chronoweave.core.TemporalHistoryManager;
import dev.chronoweave.effect.ModStatusEffects;
import dev.chronoweave.item.ChrononChargeable;
import dev.chronoweave.registry.ModBlockEntities;
import dev.chronoweave.registry.ModBlocks;
import dev.chronoweave.registry.ModDataComponents;
import dev.chronoweave.registry.ModItemGroups;
import dev.chronoweave.registry.ModItems;
import dev.chronoweave.registry.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChronoWeaveMod implements ModInitializer {
    public static final String MOD_ID = "chronoweave";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModDataComponents.init();
        ModStatusEffects.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModItems.init();
        ModItemGroups.init();
        ModWorldGen.init();

        registerEvents();
        registerCommands();

        LOGGER.info("ChronoWeave initialized: Temporal & Spatial Resonance systems online.");
    }

    private static void registerEvents() {
        // Tick temporal history snapshots, Time-Stop Domes, Singularity Vortices, and Paradox Echoes
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            TemporalHistoryManager.tickServerWorld(world);
            ParadoxEchoManager.tickServerWorld(world);
        });

        // Chrono Grace passive: 30% chance to phase-dodge incoming projectile damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.hasStatusEffect(ModStatusEffects.CHRONO_GRACE)
                    && (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.TRIDENT)
                    || source.isOf(DamageTypes.MOB_PROJECTILE) || source.isOf(DamageTypes.FIREBALL))) {
                if (entity.getRandom().nextFloat() < 0.30f && entity.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                            entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                            20, 0.3, 0.4, 0.3, 0.1);
                    serverWorld.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                            SoundCategory.PLAYERS, 0.8f, 1.6f);
                    return false;
                }
            }
            return true;
        });

        // Intercept fatal damage if player is bound to a charged Chrono-Anchor
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                if (ChronoAnchorBlockEntity.tryInterceptPlayerDeath(serverPlayer)) {
                    return false;
                }
            }
            return true;
        });
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("chronoweave")
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    int snapshots = TemporalHistoryManager.getSnapshotCount(player.getUuid());
                                    ChronoAnchorBlockEntity.AnchorLocation anchor = ChronoAnchorBlockEntity.getBoundAnchor(player.getUuid());
                                    String anchorStr = anchor == null
                                            ? "None"
                                            : anchor.pos().toShortString() + " (" + anchor.dimension().getValue() + ")";
                                    ctx.getSource().sendFeedback(() -> Text.literal("═══ ChronoWeave Telemetry ═══").formatted(Formatting.AQUA), false);
                                    ctx.getSource().sendFeedback(() -> Text.literal("• Rewind Buffer: " + snapshots + "/" + TemporalHistoryManager.MAX_SNAPSHOTS + " snapshots").formatted(Formatting.GOLD), false);
                                    ctx.getSource().sendFeedback(() -> Text.literal("• Bound Chrono-Anchor: " + anchorStr).formatted(Formatting.LIGHT_PURPLE), false);
                                    return 1;
                                }))
                        .then(CommandManager.literal("charge")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 10000))
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            ItemStack held = player.getMainHandStack();
                                            if (held.getItem() instanceof ChrononChargeable chargeable) {
                                                ChrononEnergyHelper.addCharge(held, amount, chargeable.getMaxChrononCapacity());
                                                ctx.getSource().sendFeedback(() -> Text.literal("Charged held artifact by +" + amount + " Chr.").formatted(Formatting.AQUA), false);
                                                return 1;
                                            }
                                            ctx.getSource().sendError(Text.literal("Hold a ChronoWeave artifact in your main hand."));
                                            return 0;
                                        })))
                        .then(CommandManager.literal("kit")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    player.getInventory().insertStack(ModItemGroups.createChargedStack(ModItems.CHRONOGLASS));
                                    player.getInventory().insertStack(ModItemGroups.createChargedStack(ModItems.RIFTBLADE));
                                    player.getInventory().insertStack(new ItemStack(ModItems.STASIS_PRISM));
                                    player.getInventory().insertStack(ModItemGroups.createChargedStack(ModItems.WORMHOLE_ASTROLABE));
                                    player.getInventory().insertStack(ModItemGroups.createChargedStack(ModItems.ENTROPY_MANIPULATOR));
                                    player.getInventory().insertStack(ModItemGroups.createChargedStack(ModItems.CHRONO_STRIDER_BOOTS));
                                    player.getInventory().insertStack(new ItemStack(ModBlocks.CHRONON_SINGULARITY_CORE, 2));
                                    player.getInventory().insertStack(new ItemStack(ModBlocks.TEMPORAL_ACCELERATOR, 2));
                                    player.getInventory().insertStack(new ItemStack(ModBlocks.CHRONO_ANCHOR, 1));
                                    player.getInventory().insertStack(new ItemStack(ModItems.REFINED_CHRONON_CRYSTAL, 16));
                                    ctx.getSource().sendFeedback(() -> Text.literal("Granted full ChronoWeave Master Kit!").formatted(Formatting.GOLD), true);
                                    return 1;
                                }))));
    }
}
