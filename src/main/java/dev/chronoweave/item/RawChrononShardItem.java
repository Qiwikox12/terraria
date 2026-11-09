package dev.chronoweave.item;

import dev.chronoweave.effect.ModStatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

public class RawChrononShardItem extends Item {
    public RawChrononShardItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CHRONO_GRACE, 240, 0));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 240, 1));
            user.getItemCooldownManager().set(this, 60);
            stack.decrementUnlessCreative(1, user);
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.END_ROD,
                        user.getX(), user.getBodyY(0.5), user.getZ(),
                        16, 0.3, 0.4, 0.3, 0.06);
            }
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 1.4f);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.chronoweave.raw_chronon_shard.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.chronoweave.raw_chronon_shard.2").formatted(Formatting.AQUA));
    }
}
