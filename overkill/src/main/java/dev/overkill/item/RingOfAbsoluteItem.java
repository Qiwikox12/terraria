package dev.overkill.item;

import dev.overkill.registry.ModComponents;
import dev.overkill.util.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** All passive effects are driven by {@link dev.overkill.logic.RingOfAbsolute} while the ring is anywhere in the inventory. */
public class RingOfAbsoluteItem extends Item {
	public RingOfAbsoluteItem(Properties properties) {
		super(properties);
	}

	public static boolean magnet(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MAGNET, true);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			boolean magnet = !magnet(stack);
			stack.set(ModComponents.MAGNET, magnet);
			player.displayClientMessage(Component.translatable("message.overkill.magnet", Tooltips.onOff(magnet)), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F,
					magnet ? 1.5F : 0.8F);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.overkill.magnet", Tooltips.onOff(magnet(stack))).withStyle(ChatFormatting.GRAY));
		Tooltips.describe(tooltip, "ring_of_absolute", 5);
	}
}
