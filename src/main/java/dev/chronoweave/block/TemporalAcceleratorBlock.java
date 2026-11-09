package dev.chronoweave.block;

import com.mojang.serialization.MapCodec;
import dev.chronoweave.block.entity.TemporalAcceleratorBlockEntity;
import dev.chronoweave.registry.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class TemporalAcceleratorBlock extends BlockWithEntity {
    public static final MapCodec<TemporalAcceleratorBlock> CODEC = createCodec(TemporalAcceleratorBlock::new);

    public TemporalAcceleratorBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TemporalAcceleratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlockEntities.TEMPORAL_ACCELERATOR,
                world.isClient ? null : TemporalAcceleratorBlockEntity::serverTick);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof TemporalAcceleratorBlockEntity accelerator) {
                accelerator.cycleMode(player, world, pos);
            }
        }
        return ActionResult.SUCCESS;
    }
}
