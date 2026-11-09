package dev.chronoweave.item;

import dev.chronoweave.core.ChrononEnergyHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class RefinedChrononCrystalItem extends Item {
    public static final int CHARGE_BOOST = 600;

    public RefinedChrononCrystalItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack crystalStack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(crystalStack);
        }

        int totalTransferred = 0;
        for (int i = 0; i < user.getInventory().size(); i++) {
            ItemStack slotStack = user.getInventory().getStack(i);
            if (slotStack.getItem() instanceof ChrononChargeable chargeable) {
                int max = chargeable.getMaxChrononCapacity();
                int added = ChrononEnergyHelper.addCharge(slotStack, CHARGE_BOOST - totalTransferred, max);
                totalTransferred += added;
                if (totalTransferred >= CHARGE_BOOST) {
                    break;
                }
            }
        }

        if (totalTransferred > 0) {
            crystalStack.decrementUnlessCreative(1, user);
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.END_ROD,
                        user.getX(), user.getBodyY(0.5), user.getZ(),
                        24, 0.35, 0.45, 0.35, 0.08);
            }
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.0f, 1.5f);
            user.sendMessage(Text.translatable("message.chronoweave.crystal_charged_inventory", totalTransferred)
                    .formatted(Formatting.AQUA), true);
            return TypedActionResult.success(crystalStack);
        }

        user.sendMessage(Text.translatable("message.chronoweave.crystal_nothing_to_charge").formatted(Formatting.YELLOW), true);
        return TypedActionResult.pass(crystalStack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.chronoweave.refined_chronon_crystal.1", CHARGE_BOOST).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.chronoweave.refined_chronon_crystal.2").formatted(Formatting.GOLD));
    }
}
