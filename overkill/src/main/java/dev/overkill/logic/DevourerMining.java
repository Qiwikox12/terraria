package dev.overkill.logic;

import dev.overkill.item.DevourerMode;
import dev.overkill.item.EarthDevourerItem;
import dev.overkill.registry.ModItems;
import dev.overkill.util.Advancements;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Area / vein mining for the Earth Devourer. Every block goes through the regular break events, so claim and
 * protection mods can veto individual positions. Drops (optionally smelted) and XP go straight to the player.
 */
public final class DevourerMining {
	public static final int VEIN_LIMIT = 1024;
	private static final int EXCAVATOR_THRESHOLD = 500;
	/** Guards against our own break events re-entering the area logic. */
	private static boolean busy;

	private DevourerMining() {
	}

	public static void init() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (busy || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
				return true;
			}
			ItemStack tool = player.getMainHandItem();
			if (!tool.is(ModItems.EARTH_DEVOURER)) {
				return true;
			}
			mine(serverPlayer, serverLevel, pos, faceHit(serverPlayer, pos), tool);
			// We already broke the origin ourselves (to route its drops), so vanilla must not break it again.
			return false;
		});
	}

	private static Direction faceHit(ServerPlayer player, BlockPos pos) {
		HitResult hit = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(pos)) {
			return blockHit.getDirection();
		}
		return Direction.orderedByNearest(player)[0].getOpposite();
	}

	/** @return number of blocks broken. */
	public static int mine(ServerPlayer player, ServerLevel level, BlockPos origin, Direction face, ItemStack tool) {
		busy = true;
		try {
			Set<BlockPos> targets = collect(player, level, origin, face, EarthDevourerItem.mode(tool));
			Harvest harvest = new Harvest(EarthDevourerItem.autoSmelt(tool));
			int broken = 0;
			for (BlockPos pos : targets) {
				if (breakOne(player, level, pos, origin, tool, harvest)) {
					broken++;
				}
			}
			harvest.deliver(player, level, origin, targets);
			if (broken >= EXCAVATOR_THRESHOLD) {
				Advancements.grant(player, "excavator");
			}
			return broken;
		} finally {
			busy = false;
		}
	}

	static Set<BlockPos> collect(ServerPlayer player, ServerLevel level, BlockPos origin, Direction face, DevourerMode mode) {
		Set<BlockPos> result = new LinkedHashSet<>();
		result.add(origin);
		if (mode == DevourerMode.SINGLE) {
			return result;
		}
		if (mode == DevourerMode.VEIN) {
			collectVein(level, origin, result);
			return result;
		}
		int r = mode.radius;
		int size = mode.size();
		Direction depth = face.getOpposite();
		if (face.getAxis().isVertical()) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int d = 0; d < size; d++) {
						result.add(origin.offset(dx, 0, dz).relative(depth, d));
					}
				}
			}
		} else {
			// Horizontal tunnels start at the player's feet when possible, so you never dig the floor out from under yourself.
			int bottom = Mth.clamp(player.getBlockY(), origin.getY() - (size - 1), origin.getY());
			Direction side = face.getClockWise();
			for (int s = -r; s <= r; s++) {
				for (int y = bottom; y < bottom + size; y++) {
					for (int d = 0; d < size; d++) {
						BlockPos p = origin.relative(side, s).relative(depth, d);
						result.add(new BlockPos(p.getX(), y, p.getZ()));
					}
				}
			}
		}
		return result;
	}

	private static void collectVein(ServerLevel level, BlockPos origin, Set<BlockPos> result) {
		Block block = level.getBlockState(origin).getBlock();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(origin);
		seen.add(origin);
		while (!queue.isEmpty() && result.size() < VEIN_LIMIT) {
			BlockPos current = queue.poll();
			for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
				if (seen.size() > VEIN_LIMIT * 8) {
					return;
				}
				BlockPos immutable = next.immutable();
				if (seen.add(immutable) && level.getBlockState(immutable).is(block)) {
					result.add(immutable);
					queue.add(immutable);
					if (result.size() >= VEIN_LIMIT) {
						return;
					}
				}
			}
		}
	}

	private static boolean breakOne(ServerPlayer player, ServerLevel level, BlockPos pos, BlockPos origin, ItemStack tool, Harvest harvest) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || !level.isLoaded(pos)) {
			return false;
		}
		if (state.getBlock() instanceof LiquidBlock) {
			return false;
		}
		if (!player.mayInteract(level, pos) || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
			return false;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!pos.equals(origin) && !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
			return false;
		}
		Block block = state.getBlock();
		BlockState destroyed = block.playerWillDestroy(level, pos, state, player);
		if (!level.removeBlock(pos, false)) {
			return false;
		}
		block.destroy(level, pos, destroyed);
		if (!player.isCreative()) {
			harvest.drops.addAll(Block.getDrops(destroyed, level, pos, blockEntity, player, tool));
			destroyed.spawnAfterBreak(level, pos, tool, true);
		}
		player.awardStat(Stats.BLOCK_MINED.get(block));
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
		return true;
	}

	private static final class Harvest {
		final boolean smelt;
		final List<ItemStack> drops = new ArrayList<>();

		Harvest(boolean smelt) {
			this.smelt = smelt;
		}

		void deliver(ServerPlayer player, ServerLevel level, BlockPos origin, Set<BlockPos> area) {
			float smeltXp = 0.0F;
			List<ItemStack> merged = new ArrayList<>();
			for (ItemStack drop : drops) {
				ItemStack result = drop;
				if (smelt) {
					Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
							.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level);
					if (recipe.isPresent()) {
						ItemStack out = recipe.get().value().getResultItem(level.registryAccess());
						if (!out.isEmpty()) {
							result = out.copyWithCount(out.getCount() * drop.getCount());
							smeltXp += recipe.get().value().getExperience() * drop.getCount();
						}
					}
				}
				merge(merged, result);
			}
			boolean any = false;
			for (ItemStack stack : merged) {
				any = true;
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					player.drop(stack, false);
				}
			}

			int xp = Mth.floor(smeltXp);
			if (level.random.nextFloat() < smeltXp - xp) {
				xp++;
			}
			// Ores pop their XP as orbs from spawnAfterBreak; vacuum them up so nothing is left behind.
			AABB bounds = new AABB(origin);
			for (BlockPos pos : area) {
				bounds = bounds.minmax(new AABB(pos));
			}
			for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, bounds.inflate(1.0))) {
				xp += orb.getValue();
				orb.discard();
			}
			if (xp > 0) {
				player.giveExperiencePoints(xp);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F,
						1.0F + level.random.nextFloat() * 0.4F);
			}
			if (any) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
			}
		}

		private static void merge(List<ItemStack> into, ItemStack stack) {
			for (ItemStack existing : into) {
				if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
					int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
					existing.grow(moved);
					stack = stack.copyWithCount(stack.getCount() - moved);
					if (stack.isEmpty()) {
						return;
					}
				}
			}
			into.add(stack.copy());
		}
	}
}
