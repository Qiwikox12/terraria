package dev.chronoweave.item;

import dev.chronoweave.core.ChrononEnergyHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Chrono-Strider Boots:
 * - Passive: Negates fall damage while charged.
 * - Sprinting over Water or Lava: Phase-crystallizes the liquid surface beneath feet into Ice / Obsidian.
 * - Sneak in Mid-Air while falling: Triggers a Temporal Air-Step double jump!
 */
public class ChrononBootsItem extends ArmorItem implements ChrononChargeable {
    public static final int MAX_CAPACITY = 1000;

    public ChrononBootsItem(Settings settings) {
        super(ArmorMaterials.NETHERITE, Type.BOOTS, settings);
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

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient || !(entity instanceof PlayerEntity player)) {
            return;
        }
        if (player.getEquippedStack(EquipmentSlot.FEET) != stack) {
            return;
        }
        if (ChrononEnergyHelper.getCharge(stack) < 5 && !player.isCreative()) {
            return;
        }

        // Cushion fall damage using temporal suspension
        if (player.fallDistance > 3.0f && ChrononEnergyHelper.tryConsume(stack, player, 5)) {
            player.fallDistance = 0.0f;
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 5, 0.2, 0.05, 0.2, 0.02);
            }
        }

        // Mid-Air Temporal Step (Double Jump when pressing Sneak while falling)
        if (!player.isOnGround() && player.isSneaking() && player.getVelocity().y < -0.15
                && !player.getItemCooldownManager().isCoolingDown(this)) {
            if (ChrononEnergyHelper.tryConsume(stack, player, 25)) {
                Vec3d look = player.getRotationVec(1.0f);
                player.setVelocity(player.getVelocity().x + look.x * 0.45, 0.68, player.getVelocity().z + look.z * 0.45);
                player.velocityModified = true;
                player.fallDistance = 0.0f;
                player.getItemCooldownManager().set(this, 25);
                if (world instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 18, 0.35, 0.05, 0.35, 0.05);
                }
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.55f);
            }
        }

        // Liquid Phase-Walk when sprinting
        if (player.isSprinting()) {
            BlockPos below = player.getBlockPos().down();
            for (BlockPos scan : BlockPos.iterate(below.add(-1, 0, -1), below.add(1, 0, 1))) {
                BlockState state = world.getBlockState(scan);
                if (state.isOf(Blocks.WATER) && world.getBlockState(scan.up()).isAir()) {
                    if (ChrononEnergyHelper.tryConsume(stack, player, 2)) {
                        world.setBlockState(scan, Blocks.FROSTED_ICE.getDefaultState());
                    }
                } else if (state.isOf(Blocks.LAVA) && world.getBlockState(scan.up()).isAir()) {
                    if (ChrononEnergyHelper.tryConsume(stack, player, 4)) {
                        world.setBlockState(scan, Blocks.OBSIDIAN.getDefaultState());
                    }
                }
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(ChrononEnergyHelper.buildChargeBarTooltip(ChrononEnergyHelper.getCharge(stack), MAX_CAPACITY));
        tooltip.add(Text.translatable("tooltip.chronoweave.chrono_boots.1").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.chronoweave.chrono_boots.2").formatted(Formatting.GOLD));
    }
}
