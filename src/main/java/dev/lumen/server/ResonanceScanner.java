package dev.lumen.server;

import dev.lumen.Lumen;
import dev.lumen.LumenStats;
import dev.lumen.LumenTags;
import dev.lumen.config.LumenConfig;
import dev.lumen.item.ResonanceMode;
import dev.lumen.item.ResonatorItem;
import dev.lumen.network.LumenNetworking.BlockSignal;
import dev.lumen.network.LumenNetworking.EntitySignal;
import dev.lumen.network.LumenNetworking.PulsePayload;
import dev.lumen.network.LumenNetworking.ScanResultPayload;
import dev.lumen.util.ResonanceMath;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ResonanceScanner {
    public static void scan(ServerPlayerEntity player, ItemStack stack, boolean deep) {
        ServerWorld world = player.getServerWorld();
        LumenConfig config = Lumen.config();
        ResonanceMode mode = ResonatorItem.getMode(stack);
        int radius = deep ? config.deepRange : config.range;
        int limit = Math.min(256, deep ? config.maxHighlights * 2 : config.maxHighlights);
        List<BlockSignal> blocks = new ArrayList<>();
        List<EntitySignal> entities = new ArrayList<>();

        if (mode == ResonanceMode.LIFE) {
            entities.addAll(scanLife(player, world, radius, limit));
        } else {
            blocks.addAll(scanBlocks(player, world, mode, radius, limit));
        }

        ScanResultPayload result = new ScanResultPayload(mode.id(), deep, radius, blocks, entities);
        ServerPlayNetworking.send(player, result);
        PulsePayload pulse = new PulsePayload(player.getX(), player.getEyeY(), player.getZ(), radius, mode.id(), deep);
        for (ServerPlayerEntity viewer : PlayerLookup.world(world)) {
            if (viewer.squaredDistanceTo(player) <= 64.0 * 64.0) {
                ServerPlayNetworking.send(viewer, pulse);
            }
        }

        world.playSound(null, player.getBlockPos(),
                deep ? SoundEvents.BLOCK_SCULK_CATALYST_BLOOM : SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                SoundCategory.PLAYERS, deep ? 0.75f : 0.55f, deep ? 0.75f : 1.3f);

        int signals = blocks.size() + entities.size();
        player.sendMessage(Text.translatable("message.lumen.scan_complete", signals,
                Text.translatable(deep ? "hud.lumen.deep" : "hud.lumen.quick")), true);
        LumenStats.increment(player, LumenStats.PULSES);
        if (deep) {
            LumenStats.increment(player, LumenStats.DEEP_SCANS);
        }
        LumenStats.increment(player, switch (mode) {
            case ORE -> LumenStats.ORE_SCANS;
            case CACHE -> LumenStats.CACHE_SCANS;
            case LIFE -> LumenStats.LIFE_SCANS;
        });

        grant(player, "first_pulse", "resonated");
        if (deep && signals >= 20) {
            grant(player, "deep_listener", "deep_listener");
        }
        if (hasUsedEveryMode(player)) {
            grant(player, "full_spectrum", "full_spectrum");
        }
    }

    private static List<BlockSignal> scanBlocks(ServerPlayerEntity player, ServerWorld world, ResonanceMode mode,
                                                int radius, int limit) {
        record Candidate(BlockPos pos, double distance, int priority, int color) {
        }
        List<Candidate> candidates = new ArrayList<>();
        BlockPos origin = player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    if (!ResonanceMath.insideSphere(dx, dy, dz, radius)) {
                        continue;
                    }
                    mutable.set(x, origin.getY() + dy, z);
                    BlockState state = world.getBlockState(mutable);
                    boolean matches = mode == ResonanceMode.ORE
                            ? state.isIn(LumenTags.RESONANT_ORES) : state.isIn(LumenTags.RESONANT_CACHES);
                    if (matches) {
                        candidates.add(new Candidate(mutable.toImmutable(), mutable.getSquaredDistance(origin),
                                rarity(state), color(state, mode)));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::priority).reversed()
                .thenComparingDouble(Candidate::distance));
        return candidates.stream().limit(limit)
                .map(candidate -> new BlockSignal(candidate.pos.asLong(), candidate.color)).toList();
    }

    private static List<EntitySignal> scanLife(ServerPlayerEntity player, ServerWorld world, int radius, int limit) {
        Box area = player.getBoundingBox().expand(radius);
        return world.getOtherEntities(player, area, entity -> entity instanceof LivingEntity living
                        && living.isAlive() && !(entity instanceof PlayerEntity) && !(entity instanceof ArmorStandEntity)
                        && entity.squaredDistanceTo(player) <= (double) radius * radius).stream()
                .sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                .limit(limit)
                .map(entity -> new EntitySignal(entity.getId(),
                        entity instanceof HostileEntity ? 0xFF7474 : ResonanceMode.LIFE.color()))
                .toList();
    }

    private static int rarity(BlockState state) {
        if (state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.VAULT)) {
            return 100;
        }
        if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE)
                || state.isOf(Blocks.TRIAL_SPAWNER) || state.isOf(Blocks.SPAWNER)) {
            return 90;
        }
        if (state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
                || state.isOf(Blocks.NETHER_GOLD_ORE) || state.isOf(Blocks.BUDDING_AMETHYST)) {
            return 60;
        }
        return 10;
    }

    private static int color(BlockState state, ResonanceMode mode) {
        if (state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE)) {
            return 0x65E68A;
        }
        if (state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.GOLD_ORE)
                || state.isOf(Blocks.DEEPSLATE_GOLD_ORE) || state.isOf(Blocks.NETHER_GOLD_ORE)) {
            return 0xFFD078;
        }
        if (state.isOf(Blocks.VAULT) || state.isOf(Blocks.TRIAL_SPAWNER)) {
            return 0x78CFFF;
        }
        return mode.color();
    }

    private static boolean hasUsedEveryMode(ServerPlayerEntity player) {
        return player.getStatHandler().getStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(LumenStats.ORE_SCANS)) > 0
                && player.getStatHandler().getStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(LumenStats.CACHE_SCANS)) > 0
                && player.getStatHandler().getStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(LumenStats.LIFE_SCANS)) > 0;
    }

    private static void grant(ServerPlayerEntity player, String path, String criterion) {
        Identifier id = Lumen.id(path);
        AdvancementEntry advancement = player.getServer().getAdvancementLoader().get(id);
        if (advancement != null) {
            player.getAdvancementTracker().grantCriterion(advancement, criterion);
        }
    }

    private ResonanceScanner() {
    }
}
