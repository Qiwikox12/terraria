package dev.chronoweave.block;

import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Quantum-phased glass: impenetrable to hostile mobs and explosions, but players who are
 * sneaking or blessed with Chrono Grace can walk and phase directly through it.
 */
public class PhaseGlassBlock extends Block {
    public PhaseGlassBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (context instanceof EntityShapeContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof PlayerEntity player) {
                if (player.isSneaking() || player.hasStatusEffect(ModStatusEffects.CHRONO_GRACE)) {
                    return VoxelShapes.empty();
                }
            }
        }
        return VoxelShapes.fullCube();
    }

    @Override
    protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
}
