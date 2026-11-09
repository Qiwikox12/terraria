package dev.chronoweave.block;

import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class ChrononOreBlock extends ExperienceDroppingBlock {
    public ChrononOreBlock(Settings settings) {
        super(UniformIntProvider.create(3, 7), settings);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && entity instanceof PlayerEntity player && world.getTime() % 20 == 0) {
            player.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 80, 0, true, false, true));
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    protected void onStacksDropped(BlockState state, ServerWorld world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.onStacksDropped(state, world, pos, tool, dropExperience);
        world.spawnParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.05);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(4) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            world.addParticle(ParticleTypes.PORTAL, x, y, z, 0.0, 0.05, 0.0);
            if (random.nextBoolean()) {
                world.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.02, 0.0);
            }
        }
    }
}
