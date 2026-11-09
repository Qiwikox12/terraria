package dev.overkill.item;

import dev.overkill.logic.GravityGrab;
import dev.overkill.util.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class GravityGauntletItem extends Item {
	public static final int REPULSE_COOLDOWN = 30;

	public GravityGauntletItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			boolean acted;
			if (GravityGrab.isHolding(serverPlayer)) {
				acted = player.isShiftKeyDown() ? GravityGrab.release(serverPlayer) : GravityGrab.launch(serverPlayer);
			} else if (player.isShiftKeyDown()) {
				acted = GravityGrab.repulse(serverLevel, serverPlayer);
				if (acted) {
					player.getCooldowns().addCooldown(this, REPULSE_COOLDOWN);
				}
			} else {
				acted = GravityGrab.grab(serverLevel, serverPlayer);
			}
			return acted ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Tooltips.describe(tooltip, "gravity_gauntlet", 4);
	}
}
