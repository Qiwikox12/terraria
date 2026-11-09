package dev.overkill.effect;

import dev.overkill.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A 7x7x7 field where block entities run 16x faster and random ticks (crops, saplings, ...) fire ~150x as often. */
public class TimeAccelerationEffect implements WorldEffect {
	public static final int DURATION = 600;
	public static final int RADIUS = 3;
	public static final int EXTRA_BLOCK_ENTITY_TICKS = 15;
	/** Each block gets a random tick with probability 1/RANDOM_TICK_CHANCE every game tick. */
	private static final int RANDOM_TICK_CHANCE = 3;

	private final BlockPos center;
	private int age;

	private TimeAccelerationEffect(BlockPos center) {
		this.center = center.immutable();
	}

	/** Re-using the hourglass on an active field refreshes it instead of stacking a second one. */
	public static void start(ServerLevel level, BlockPos center) {
		for (TimeAccelerationEffect effect : WorldEffects.active(level, TimeAccelerationEffect.class)) {
			if (effect.center.equals(center)) {
				effect.age = 0;
				effect.chime(level);
				return;
			}
		}
		TimeAccelerationEffect effect = new TimeAccelerationEffect(center);
		effect.chime(level);
		WorldEffects.add(level, effect);
	}

	private void chime(ServerLevel level) {
		Vec3 c = Vec3.atCenterOf(center);
		Fx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 1.6F);
		Fx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5F, 2.0F);
		Fx.particle(level, ParticleTypes.GLOW, c, 40, RADIUS, 0.05);
	}

	@Override
	public boolean tick(ServerLevel level) {
		age++;
		if (!level.isLoaded(center)) {
			return age < DURATION;
		}
		RandomSource random = level.random;
		for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
			BlockState state = level.getBlockState(mutable);
			if (state.isAir()) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (state.isRandomlyTicking() && random.nextInt(RANDOM_TICK_CHANCE) == 0) {
				state.randomTick(level, pos, random);
			}
			FluidState fluid = state.getFluidState();
			if (fluid.isRandomlyTicking() && random.nextInt(RANDOM_TICK_CHANCE) == 0) {
				fluid.randomTick(level, pos, random);
			}
			if (state.hasBlockEntity()) {
				tickBlockEntity(level, pos);
			}
		}
		visuals(level);
		return age < DURATION;
	}

	private static void tickBlockEntity(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) {
			return;
		}
		for (int i = 0; i < EXTRA_BLOCK_ENTITY_TICKS; i++) {
			if (blockEntity.isRemoved() || level.getBlockEntity(pos) != blockEntity) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			BlockEntityTicker<BlockEntity> ticker = ticker(state, level, blockEntity.getType());
			if (ticker == null) {
				return;
			}
			ticker.tick(level, pos, state, blockEntity);
		}
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private static <T extends BlockEntity> BlockEntityTicker<T> ticker(BlockState state, Level level, BlockEntityType<?> type) {
		return state.getTicker(level, (BlockEntityType<T>) type);
	}

	private void visuals(ServerLevel level) {
		if (age % 3 == 0) {
			double e = RADIUS + 0.5;
			Vec3 c = Vec3.atCenterOf(center);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sy = -1; sy <= 1; sy += 2) {
					for (int sz = -1; sz <= 1; sz += 2) {
						Fx.particle(level, ParticleTypes.GLOW, c.x + sx * e, c.y + sy * e, c.z + sz * e, 1, 0.05, 0.0);
					}
				}
			}
			Fx.particle(level, ParticleTypes.ENCHANT, c, 6, RADIUS * 0.6, 0.6);
		}
		if (age % 10 == 0) {
			Fx.sound(level, Vec3.atCenterOf(center), SoundEvents.UI_BUTTON_CLICK, SoundSource.BLOCKS, 0.35F, (age / 10) % 2 == 0 ? 2.0F : 1.7F);
		}
	}

	public BlockPos center() {
		return center;
	}
}
