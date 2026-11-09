package dev.chronoweave.item;

import dev.chronoweave.core.ChrononEnergyHelper;
import dev.chronoweave.core.EntropyTransformationRegistry;
import dev.chronoweave.registry.ModDataComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Entropy Manipulator Wand:
 * - Mode 0 (FORWARD ENTROPY): Ages blocks along geological/oxidation chains (e.g., Coal Block -> Diamond Ore, Stone -> Cobble -> Sand -> Glass)
 *   or rapidly grows crops, saplings, and budding amethyst.
 * - Mode 1 (REVERSE ENTROPY): Reverses entropy (repairs Damaged Anvils -> Anvils, Oxidized Copper -> Copper, Cracked Bricks -> Bricks).
 * - Mode 2 (VEIN RESONANCE): Cascades block breaking across up to 12 connected matching blocks when mining ores or logs.
 */
public class EntropyManipulatorItem extends Item implements ChrononChargeable {
    public static final int MAX_CAPACITY = 1000;
    public static final int MODE_FORWARD = 0;
    public static final int MODE_REVERSE = 1;
    public static final int MODE_VEIN = 2;

    public EntropyManipulatorItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getMaxChrononCapacity() {
        return MAX_CAPACITY;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return ChrononEnergyHelper.getBarStep(stack, MAX_CAPACITY);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return ChrononEnergyHelper.getBarColor(stack, MAX_CAPACITY);
    }

    public static int getMode(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(ModDataComponents.ARTIFACT_MODE, MODE_FORWARD), 3);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient) {
                int next = (getMode(stack) + 1) % 3;
                stack.set(ModDataComponents.ARTIFACT_MODE, next);
                user.sendMessage(Text.translatable("message.chronoweave.entropy_mode", getModeName(next))
                        .formatted(Formatting.AQUA), true);
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.9f, 1.2f + next * 0.2f);
            }
            return TypedActionResult.success(stack, world.isClient());
        }
        return TypedActionResult.pass(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();

        if (player != null && player.isSneaking()) {
            return ActionResult.PASS;
        }

        BlockState state = world.getBlockState(pos);
        int mode = getMode(stack);
        boolean reverse = (mode == MODE_REVERSE);

        Optional<Block> transformed = reverse
                ? EntropyTransformationRegistry.getReverseResult(state.getBlock())
                : EntropyTransformationRegistry.getForwardResult(state.getBlock());

        if (transformed.isPresent()) {
            int cost = EntropyTransformationRegistry.getChrononCost(state.getBlock(), reverse);
            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                if (!ChrononEnergyHelper.tryConsume(stack, player, cost)) {
                    if (player != null) {
                        player.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", cost).formatted(Formatting.RED), true);
                    }
                    return ActionResult.FAIL;
                }
                world.setBlockState(pos, transformed.get().getDefaultState());
                serverWorld.spawnParticles(reverse ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        18, 0.35, 0.35, 0.35, 0.06);
                world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.BLOCKS, 1.0f, reverse ? 0.85f : 1.45f);
            }
            return ActionResult.success(world.isClient);
        }

        // If Forward Mode is used on crops, saplings, or random-tickable blocks, accelerate growth!
        if (mode == MODE_FORWARD && (state.getBlock() instanceof Fertilizable || state.hasRandomTicks())) {
            if (!world.isClient && world instanceof ServerWorld serverWorld) {
                if (!ChrononEnergyHelper.tryConsume(stack, player, 25)) {
                    if (player != null) {
                        player.sendMessage(Text.translatable("message.chronoweave.insufficient_charge", 25).formatted(Formatting.RED), true);
                    }
                    return ActionResult.FAIL;
                }
                if (state.getBlock() instanceof Fertilizable fertilizable
                        && fertilizable.isFertilizable(world, pos, state)
                        && fertilizable.canGrow(world, world.getRandom(), pos, state)) {
                    fertilizable.grow(serverWorld, world.getRandom(), pos, state);
                } else {
                    for (int i = 0; i < 8; i++) {
                        BlockState current = world.getBlockState(pos);
                        if (current.hasRandomTicks()) {
                            current.randomTick(serverWorld, pos, world.getRandom());
                        }
                    }
                }
                serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        12, 0.35, 0.35, 0.35, 0.05);
                world.playSound(null, pos, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 1.0f, 1.3f);
            }
            return ActionResult.success(world.isClient);
        }

        return ActionResult.PASS;
    }

    @Override
    public boolean postMine(ItemStack stack, World world, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!world.isClient && world instanceof ServerWorld serverWorld
                && miner instanceof PlayerEntity player
                && getMode(stack) == MODE_VEIN
                && state.getHardness(world, pos) > 0.0f) {
            cascadeVeinBreak(serverWorld, pos, state.getBlock(), player, stack);
        }
        return super.postMine(stack, world, state, pos, miner);
    }

    private static void cascadeVeinBreak(ServerWorld world, BlockPos origin, Block targetBlock, PlayerEntity player, ItemStack stack) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);

        int broken = 0;
        int maxVein = 12;
        while (!queue.isEmpty() && broken < maxVein) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (visited.add(neighbor) && world.getBlockState(neighbor).isOf(targetBlock)) {
                            if (!ChrononEnergyHelper.tryConsume(stack, player, 8)) {
                                return;
                            }
                            world.breakBlock(neighbor, true, player);
                            world.spawnParticles(ParticleTypes.END_ROD,
                                    neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5,
                                    4, 0.2, 0.2, 0.2, 0.02);
                            queue.add(neighbor);
                            broken++;
                            if (broken >= maxVein) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static Text getModeName(int mode) {
        return switch (mode) {
            case MODE_REVERSE -> Text.translatable("mode.chronoweave.entropy.reverse");
            case MODE_VEIN -> Text.translatable("mode.chronoweave.entropy.vein");
            default -> Text.translatable("mode.chronoweave.entropy.forward");
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(ChrononEnergyHelper.buildChargeBarTooltip(ChrononEnergyHelper.getCharge(stack), MAX_CAPACITY));
        tooltip.add(Text.translatable("tooltip.chronoweave.entropy_manipulator.mode", getModeName(getMode(stack)))
                .formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.chronoweave.entropy_manipulator.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.chronoweave.entropy_manipulator.2").formatted(Formatting.GOLD));
    }
}
