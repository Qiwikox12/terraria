package dev.chronoweave.block.entity;

import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.item.ChrononChargeable;
import dev.chronoweave.registry.ModBlockEntities;
import dev.chronoweave.registry.ModBlocks;
import dev.chronoweave.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

public class ChrononSingularityCoreBlockEntity extends BlockEntity {
    public static final int MAX_FLUX = 10000;
    private int storedFlux = 1000;
    private int lastGenerationRate = 2;

    public ChrononSingularityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHRONON_SINGULARITY_CORE, pos, state);
    }

    public int getStoredFlux() {
        return storedFlux;
    }

    public int addFlux(int amount) {
        int before = this.storedFlux;
        this.storedFlux = MathHelper.clamp(this.storedFlux + amount, 0, MAX_FLUX);
        if (this.storedFlux != before) {
            markDirty();
        }
        return this.storedFlux - before;
    }

    public boolean consumeFlux(int amount) {
        if (this.storedFlux < amount) {
            return false;
        }
        this.storedFlux -= amount;
        markDirty();
        return true;
    }

    public static int calculateResonanceRate(int adjacentAmethystCount, int adjacentResonantBlocks, boolean openNightSkyOrEnd) {
        int base = 2;
        int bonus = adjacentAmethystCount * 2 + adjacentResonantBlocks * 4;
        int total = base + bonus;
        return openNightSkyOrEnd ? total * 2 : total;
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, ChrononSingularityCoreBlockEntity core) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        long time = world.getTime();
        if (time % 20 == 0) {
            int amethystCount = 0;
            int resonantCount = 0;
            for (Direction dir : Direction.values()) {
                Block neighbor = world.getBlockState(pos.offset(dir)).getBlock();
                if (neighbor == Blocks.AMETHYST_CLUSTER || neighbor == Blocks.BUDDING_AMETHYST || neighbor == Blocks.AMETHYST_BLOCK) {
                    amethystCount++;
                } else if (neighbor == ModBlocks.RESONANT_CHRONON_BLOCK) {
                    resonantCount++;
                }
            }
            boolean cosmicAlignment = (world.isSkyVisible(pos.up()) && world.isNight())
                    || world.getRegistryKey() == World.END;
            core.lastGenerationRate = calculateResonanceRate(amethystCount, resonantCount, cosmicAlignment);
            core.addFlux(core.lastGenerationRate * 5);

            // Check for nearby lightning strikes to absorb massive Chronon surges
            List<LightningEntity> bolts = world.getEntitiesByClass(LightningEntity.class, Box.of(pos.toCenterPos(), 16, 16, 16), e -> true);
            if (!bolts.isEmpty()) {
                core.addFlux(2500);
                serverWorld.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.15);
                world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.2f, 1.6f);
            }

            // Wireless Chronon recharging for players within 8 blocks
            if (core.storedFlux >= 20) {
                List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, Box.of(pos.toCenterPos(), 16, 16, 16), PlayerEntity::isAlive);
                for (PlayerEntity player : players) {
                    core.rechargePlayerInventory(serverWorld, pos, player);
                }
            }
            world.updateComparators(pos, state.getBlock());
        }

        if (time % 10 == 0) {
            double ratio = (double) core.storedFlux / MAX_FLUX;
            serverWorld.spawnParticles(ParticleTypes.PORTAL,
                    pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5,
                    3 + (int) (ratio * 6), 0.25, 0.25, 0.25, 0.15);
            if (ratio > 0.5) {
                serverWorld.spawnParticles(ParticleTypes.END_ROD,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                        1, 0.15, 0.15, 0.15, 0.02);
            }
        }
    }

    private void rechargePlayerInventory(ServerWorld world, BlockPos corePos, PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (this.storedFlux < 10) {
                break;
            }
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() instanceof ChrononChargeable chargeable) {
                int max = chargeable.getMaxChrononCapacity();
                int current = ChrononEnergyHelper.getCharge(stack);
                if (current < max) {
                    int transfer = Math.min(40, Math.min(this.storedFlux, max - current));
                    if (transfer > 0) {
                        ChrononEnergyHelper.addCharge(stack, transfer, max);
                        this.storedFlux -= transfer;
                        markDirty();
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                                player.getX(), player.getBodyY(0.5), player.getZ(),
                                3, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
        }
    }

    public boolean tryInfuseItem(World world, BlockPos pos, PlayerEntity player, Hand hand, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Direct battery charging if player clicks Core with a ChronoWeave artifact
        if (stack.getItem() instanceof ChrononChargeable chargeable) {
            int max = chargeable.getMaxChrononCapacity();
            int needed = max - ChrononEnergyHelper.getCharge(stack);
            if (needed > 0 && this.storedFlux > 0) {
                int transfer = Math.min(needed, this.storedFlux);
                ChrononEnergyHelper.addCharge(stack, transfer, max);
                this.storedFlux -= transfer;
                markDirty();
                world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0f, 1.4f);
                player.sendMessage(Text.translatable("message.chronoweave.core_recharged_item", transfer)
                        .formatted(Formatting.AQUA), true);
                return true;
            }
            return false;
        }

        // Fueling the Core manually with Raw Chronon Shards or Echo Shards
        if (stack.isOf(ModItems.RAW_CHRONON_SHARD) && this.storedFlux < MAX_FLUX) {
            stack.decrementUnlessCreative(1, player);
            addFlux(400);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.0f, 1.3f);
            sendTelemetry(player, world, pos);
            return true;
        }
        if (stack.isOf(Items.ECHO_SHARD) && this.storedFlux < MAX_FLUX) {
            stack.decrementUnlessCreative(1, player);
            addFlux(800);
            world.playSound(null, pos, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.BLOCKS, 1.0f, 1.2f);
            sendTelemetry(player, world, pos);
            return true;
        }

        // Ritual Transmutations on the Singularity Core
        Item outputItem = null;
        int fluxCost = 0;
        if (stack.isOf(Items.AMETHYST_SHARD)) {
            outputItem = ModItems.REFINED_CHRONON_CRYSTAL;
            fluxCost = 250;
        } else if (stack.isOf(Items.CLOCK)) {
            outputItem = ModItems.CHRONOGLASS;
            fluxCost = 600;
        } else if (stack.isOf(Items.ENDER_PEARL)) {
            outputItem = ModItems.STASIS_PRISM;
            fluxCost = 400;
        } else if (stack.isOf(Items.COMPASS) || stack.isOf(Items.RECOVERY_COMPASS)) {
            outputItem = ModItems.WORMHOLE_ASTROLABE;
            fluxCost = 700;
        } else if (stack.isOf(Items.NETHERITE_SWORD) || stack.isOf(Items.DIAMOND_SWORD)) {
            outputItem = ModItems.RIFTBLADE;
            fluxCost = 1200;
        }

        if (outputItem != null) {
            if (!consumeFlux(fluxCost)) {
                player.sendMessage(Text.translatable("message.chronoweave.insufficient_core_flux", fluxCost, this.storedFlux)
                        .formatted(Formatting.RED), true);
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.BLOCKS, 0.8f, 0.7f);
                return true;
            }
            stack.decrementUnlessCreative(1, player);
            ItemStack result = new ItemStack(outputItem);
            if (outputItem instanceof ChrononChargeable chargeable) {
                ChrononEnergyHelper.setCharge(result, chargeable.getMaxChrononCapacity() / 2, chargeable.getMaxChrononCapacity());
            }
            if (stack.isEmpty()) {
                player.setStackInHand(hand, result);
            } else if (!player.getInventory().insertStack(result)) {
                player.dropItem(result, false);
            }
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                serverWorld.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 25, 0.35, 0.35, 0.35, 0.12);
            }
            world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 0.85f, 1.35f);
            player.sendMessage(Text.translatable("message.chronoweave.infusion_complete", result.getName())
                    .formatted(Formatting.GOLD), true);
            return true;
        }
        return false;
    }

    public void sendTelemetry(PlayerEntity player, World world, BlockPos pos) {
        player.sendMessage(
                Text.translatable("message.chronoweave.core_status", this.storedFlux, MAX_FLUX, this.lastGenerationRate * 5)
                        .formatted(Formatting.AQUA),
                true
        );
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 1.2f);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("StoredFlux", this.storedFlux);
        nbt.putInt("LastRate", this.lastGenerationRate);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        this.storedFlux = MathHelper.clamp(nbt.getInt("StoredFlux"), 0, MAX_FLUX);
        this.lastGenerationRate = Math.max(1, nbt.getInt("LastRate"));
    }
}
