package dev.chronoweave.block.entity;

import dev.chronoweave.effect.ModStatusEffects;
import dev.chronoweave.registry.ModBlockEntities;
import dev.chronoweave.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial-temporal safety anchor. Bound players who suffer lethal damage in the same dimension
 * have their death intercepted and rewound back to the Chrono-Anchor if it holds >= 500 Chronon charge.
 */
public class ChronoAnchorBlockEntity extends BlockEntity {
    public record AnchorLocation(RegistryKey<World> dimension, BlockPos pos) {
    }

    private static final Map<UUID, AnchorLocation> PLAYER_ANCHORS = new ConcurrentHashMap<>();
    public static final int MAX_CHARGE = 3000;
    public static final int DEATH_REWIND_COST = 500;

    private int charge = 1000;

    public ChronoAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHRONO_ANCHOR, pos, state);
    }

    public int getCharge() {
        return charge;
    }

    public boolean tryFuelWithItem(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
        int gain = 0;
        if (stack.isOf(ModItems.RAW_CHRONON_SHARD)) {
            gain = 300;
        } else if (stack.isOf(ModItems.REFINED_CHRONON_CRYSTAL)) {
            gain = 1000;
        }
        if (gain > 0 && this.charge < MAX_CHARGE) {
            stack.decrementUnlessCreative(1, player);
            this.charge = MathHelper.clamp(this.charge + gain, 0, MAX_CHARGE);
            markDirty();
            world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0f, 1.3f);
            player.sendMessage(Text.translatable("message.chronoweave.anchor_charged", this.charge, MAX_CHARGE)
                    .formatted(Formatting.AQUA), true);
            return true;
        }
        return false;
    }

    public void bindPlayer(World world, BlockPos pos, PlayerEntity player) {
        PLAYER_ANCHORS.put(player.getUuid(), new AnchorLocation(world.getRegistryKey(), pos.toImmutable()));
        world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 1.0f, 1.25f);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.06);
        }
        player.sendMessage(Text.translatable("message.chronoweave.anchor_bound",
                        pos.getX(), pos.getY(), pos.getZ(), this.charge, MAX_CHARGE)
                .formatted(Formatting.GOLD), true);
    }

    public static boolean tryInterceptPlayerDeath(ServerPlayerEntity player) {
        AnchorLocation loc = PLAYER_ANCHORS.get(player.getUuid());
        if (loc == null) {
            return false;
        }
        ServerWorld targetWorld = player.getServer() != null ? player.getServer().getWorld(loc.dimension()) : null;
        if (targetWorld == null) {
            return false;
        }
        BlockEntity be = targetWorld.getBlockEntity(loc.pos());
        if (!(be instanceof ChronoAnchorBlockEntity anchor)) {
            return false;
        }
        if (anchor.charge < DEATH_REWIND_COST) {
            return false;
        }

        anchor.charge -= DEATH_REWIND_COST;
        anchor.markDirty();

        // Leave a temporal shockwave at the fatal impact site
        ServerWorld deathWorld = player.getServerWorld();
        deathWorld.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        deathWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.4, 0.4, 0.4, 0.2);
        deathWorld.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.25f);

        // Restore player and rewind to Chrono-Anchor
        player.setHealth(Math.max(8.0f, player.getMaxHealth() * 0.6f));
        player.clearStatusEffects();
        player.extinguish();
        player.fallDistance = 0.0f;
        player.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 300, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 160, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 300, 0));

        BlockPos dest = loc.pos().up();
        player.teleport(targetWorld, dest.getX() + 0.5, dest.getY() + 0.1, dest.getZ() + 0.5, player.getYaw(), player.getPitch());
        targetWorld.spawnParticles(ParticleTypes.END_ROD, dest.getX() + 0.5, dest.getY() + 0.8, dest.getZ() + 0.5, 35, 0.4, 0.5, 0.4, 0.1);
        targetWorld.playSound(null, dest, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 1.2f, 1.3f);

        player.sendMessage(Text.translatable("message.chronoweave.death_rewound", anchor.charge)
                .formatted(Formatting.LIGHT_PURPLE), false);
        return true;
    }

    public static AnchorLocation getBoundAnchor(UUID playerId) {
        return PLAYER_ANCHORS.get(playerId);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, ChronoAnchorBlockEntity anchor) {
        if (!(world instanceof ServerWorld serverWorld) || world.getTime() % 40 != 0) {
            return;
        }
        // Passively siphon flux from any Chronon Singularity Core within 6 blocks
        if (anchor.charge < MAX_CHARGE) {
            for (BlockPos scan : BlockPos.iterate(pos.add(-6, -2, -6), pos.add(6, 2, 6))) {
                BlockEntity be = world.getBlockEntity(scan);
                if (be instanceof ChrononSingularityCoreBlockEntity core && core.getStoredFlux() >= 50) {
                    int needed = Math.min(50, MAX_CHARGE - anchor.charge);
                    if (core.consumeFlux(needed)) {
                        anchor.charge += needed;
                        anchor.markDirty();
                        break;
                    }
                }
            }
        }
        if (anchor.charge >= DEATH_REWIND_COST) {
            serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                    3, 0.2, 0.2, 0.2, 0.02);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("AnchorCharge", this.charge);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        this.charge = MathHelper.clamp(nbt.getInt("AnchorCharge"), 0, MAX_CHARGE);
    }
}
