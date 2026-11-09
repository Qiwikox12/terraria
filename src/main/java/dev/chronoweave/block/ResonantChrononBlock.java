package dev.chronoweave.block;

import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class ResonantChrononBlock extends Block {
    public ResonantChrononBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && entity instanceof LivingEntity living && world.getTime() % 15 == 0) {
            living.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 120, 0, true, true, true));
            if (entity.isSneaking()) {
                // Vertical harmonic launch pad when sneaking on Resonant Chronon Block
                entity.setVelocity(entity.getVelocity().x, 0.85, entity.getVelocity().z);
                entity.velocityModified = true;
                world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.2f, 1.5f);
            }
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6,
                    pos.getY() + 1.05,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.03, 0.0);
        }
    }
}
