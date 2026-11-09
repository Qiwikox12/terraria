package dev.chronoweave.block.entity;

import dev.chronoweave.effect.ModStatusEffects;
import dev.chronoweave.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Multi-mode field generator:
 * Mode 0 (OVERCLOCK): 6x extra ticks on neighboring BlockEntities (furnaces, brewing stands, smokers) + accelerated crop/amethyst ticks.
 * Mode 1 (STASIS_FIELD): Freezes hostile mobs and projectiles in a 6-block sanctuary while blessing players with Chrono Grace.
 * Mode 2 (ENTROPY_HARVEST): Siphons mature crop/plant entropy to feed nearby Chronon Singularity Cores.
 */
public class TemporalAcceleratorBlockEntity extends BlockEntity {
    public static final int MODE_OVERCLOCK = 0;
    public static final int MODE_STASIS_FIELD = 1;
    public static final int MODE_ENTROPY_HARVEST = 2;

    private int mode = MODE_OVERCLOCK;

    public TemporalAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEMPORAL_ACCELERATOR, pos, state);
    }

    public int getMode() {
        return mode;
    }

    public void cycleMode(PlayerEntity player, World world, BlockPos pos) {
        this.mode = (this.mode + 1) % 3;
        markDirty();
        String modeKey = switch (this.mode) {
            case MODE_STASIS_FIELD -> "mode.chronoweave.accelerator.stasis";
            case MODE_ENTROPY_HARVEST -> "mode.chronoweave.accelerator.harvest";
            default -> "mode.chronoweave.accelerator.overclock";
        };
        player.sendMessage(Text.translatable("message.chronoweave.accelerator_mode", Text.translatable(modeKey))
                .formatted(Formatting.AQUA), true);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.9f, 1.25f + this.mode * 0.2f);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, TemporalAcceleratorBlockEntity accelerator) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        // Redstone signal pauses the Temporal Accelerator
        if (world.isReceivingRedstonePower(pos)) {
            return;
        }

        long time = world.getTime();
        if (accelerator.mode == MODE_OVERCLOCK) {
            tickOverclockField(serverWorld, pos, time);
        } else if (accelerator.mode == MODE_STASIS_FIELD) {
            tickStasisField(serverWorld, pos, time);
        } else if (accelerator.mode == MODE_ENTROPY_HARVEST && time % 20 == 0) {
            tickEntropyHarvest(serverWorld, pos);
        }
    }

    private static void tickOverclockField(ServerWorld world, BlockPos center, long time) {
        // Every 2 ticks, accelerate adjacent & nearby BlockEntities (3x3x3 around accelerator)
        if (time % 2 == 0) {
            for (BlockPos targetPos : BlockPos.iterate(center.add(-2, -1, -2), center.add(2, 1, 2))) {
                if (targetPos.equals(center)) {
                    continue;
                }
                BlockEntity neighborBe = world.getBlockEntity(targetPos);
                if (neighborBe != null && !(neighborBe instanceof TemporalAcceleratorBlockEntity)
                        && !(neighborBe instanceof ChrononSingularityCoreBlockEntity)) {
                    BlockState targetState = world.getBlockState(targetPos);
                    for (int i = 0; i < 4; i++) {
                        tickBlockEntityUnchecked(world, targetPos, targetState, neighborBe);
                    }
                    if (time % 20 == 0) {
                        world.spawnParticles(ParticleTypes.END_ROD,
                                targetPos.getX() + 0.5, targetPos.getY() + 0.65, targetPos.getZ() + 0.5,
                                2, 0.2, 0.2, 0.2, 0.01);
                    }
                }
            }
        }

        // Every 10 ticks, grant randomTicks to crops, saplings, amethyst, copper in a 9x3x9 zone
        if (time % 10 == 0) {
            for (int i = 0; i < 6; i++) {
                int dx = world.getRandom().nextBetween(-4, 4);
                int dy = world.getRandom().nextBetween(-1, 2);
                int dz = world.getRandom().nextBetween(-4, 4);
                BlockPos targetPos = center.add(dx, dy, dz);
                BlockState targetState = world.getBlockState(targetPos);
                if (targetState.hasRandomTicks()) {
                    targetState.randomTick(world, targetPos, world.getRandom());
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                            2, 0.2, 0.2, 0.2, 0.02);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickBlockEntityUnchecked(World world, BlockPos pos, BlockState state, T blockEntity) {
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) state.getBlockEntityTicker(world, (BlockEntityType<T>) blockEntity.getType());
        if (ticker != null) {
            ticker.tick(world, pos, state, blockEntity);
        }
    }

    private static void tickStasisField(ServerWorld world, BlockPos center, long time) {
        Box field = Box.of(center.toCenterPos(), 12.0, 8.0, 12.0);
        for (Entity entity : world.getOtherEntities(null, field)) {
            if (entity instanceof PlayerEntity player) {
                if (time % 20 == 0) {
                    player.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 60, 0, true, false, true));
                }
            } else if (entity instanceof LivingEntity living && entity instanceof Monster) {
                living.addStatusEffect(new StatusEffectInstance(ModStatusEffects.TIME_LOCK, 15, 0, true, false, true));
            } else if (entity instanceof ProjectileEntity projectile) {
                if (!(projectile.getOwner() instanceof PlayerEntity)) {
                    projectile.setVelocity(projectile.getVelocity().multiply(0.15));
                    projectile.velocityModified = true;
                }
            }
        }
        if (time % 8 == 0) {
            double angle = (time * 0.2) % (Math.PI * 2.0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    center.getX() + 0.5 + Math.cos(angle) * 5.5,
                    center.getY() + 0.6,
                    center.getZ() + 0.5 + Math.sin(angle) * 5.5,
                    3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private static void tickEntropyHarvest(ServerWorld world, BlockPos center) {
        int harvestedFlux = 0;
        for (BlockPos scan : BlockPos.iterate(center.add(-3, -1, -3), center.add(3, 1, 3))) {
            BlockState state = world.getBlockState(scan);
            if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                world.setBlockState(scan, crop.withAge(0), 2);
                harvestedFlux += 120;
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        scan.getX() + 0.5, scan.getY() + 0.5, scan.getZ() + 0.5,
                        8, 0.2, 0.2, 0.2, 0.05);
            }
        }
        if (harvestedFlux > 0) {
            for (BlockPos coreScan : BlockPos.iterate(center.add(-6, -3, -6), center.add(6, 3, 6))) {
                BlockEntity be = world.getBlockEntity(coreScan);
                if (be instanceof ChrononSingularityCoreBlockEntity core) {
                    core.addFlux(harvestedFlux);
                    Vec3d start = center.toCenterPos();
                    world.spawnParticles(ParticleTypes.END_ROD, start.x, start.y + 0.6, start.z, 6, 0.1, 0.1, 0.1, 0.04);
                    break;
                }
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("AcceleratorMode", this.mode);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        this.mode = Math.floorMod(nbt.getInt("AcceleratorMode"), 3);
    }
}
