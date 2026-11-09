package dev.overkill.item;

import dev.overkill.util.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class OverkillCoreItem extends Item {
	public OverkillCoreItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Tooltips.describe(tooltip, "overkill_core", 1);
	}
}
