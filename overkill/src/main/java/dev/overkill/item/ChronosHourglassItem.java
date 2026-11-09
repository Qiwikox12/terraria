package dev.overkill.item;

import dev.overkill.effect.TimeAccelerationEffect;
import dev.overkill.effect.TimeStopEffect;
import dev.overkill.effect.WorldEffects;
import dev.overkill.util.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class ChronosHourglassItem extends Item {
	public static final int TIME_STOP_COOLDOWN = 900;
	public static final int ACCELERATION_COOLDOWN = 40;

	public ChronosHourglassItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isShiftKeyDown()) {
			// Plain right-click on a block still stops time.
			return InteractionResult.PASS;
		}
		if (context.getLevel() instanceof ServerLevel level) {
			WorldEffects.add(level, new TimeAccelerationEffect(context.getClickedPos()));
			player.displayClientMessage(Component.translatable("message.overkill.time_accelerated").withStyle(ChatFormatting.AQUA), true);
		}
		player.getCooldowns().addCooldown(this, ACCELERATION_COOLDOWN);
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			return InteractionResultHolder.pass(stack);
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			if (TimeStopEffect.isCaster(serverLevel, serverPlayer)) {
				return InteractionResultHolder.fail(stack);
			}
			WorldEffects.add(serverLevel, new TimeStopEffect(serverPlayer));
		}
		player.getCooldowns().addCooldown(this, TIME_STOP_COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Tooltips.describe(tooltip, "chronos_hourglass", 4);
	}
}
