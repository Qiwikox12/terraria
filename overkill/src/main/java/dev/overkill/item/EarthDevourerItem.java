package dev.overkill.item;

import dev.overkill.OverkillMod;
import dev.overkill.registry.ModComponents;
import dev.overkill.util.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Mines anything breakable instantly, in growing cubes or whole veins, straight into the inventory. */
public class EarthDevourerItem extends DiggerItem {
	public EarthDevourerItem(Properties properties) {
		super(OverkillTier.INSTANCE, BlockTags.MINEABLE_WITH_PICKAXE, properties
				.attributes(createAttributes())
				.component(DataComponents.UNBREAKABLE, new Unbreakable(false)));
	}

	private static ItemAttributeModifiers createAttributes() {
		return ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE,
						new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 11.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED,
						new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.BLOCK_INTERACTION_RANGE,
						new AttributeModifier(OverkillMod.id("devourer_reach"), 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.SUBMERGED_MINING_SPEED,
						new AttributeModifier(OverkillMod.id("devourer_underwater"), 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND)
				.build();
	}

	public static DevourerMode mode(ItemStack stack) {
		return DevourerMode.byIndex(stack.getOrDefault(ModComponents.DEVOURER_MODE, 0));
	}

	public static boolean autoSmelt(ItemStack stack) {
		return stack.getOrDefault(ModComponents.AUTO_SMELT, false);
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		return OverkillTier.INSTANCE.getSpeed();
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			Component message;
			if (player.isShiftKeyDown()) {
				boolean smelt = !autoSmelt(stack);
				stack.set(ModComponents.AUTO_SMELT, smelt);
				message = Component.translatable("message.overkill.auto_smelt", Tooltips.onOff(smelt));
			} else {
				DevourerMode next = mode(stack).next();
				stack.set(ModComponents.DEVOURER_MODE, next.ordinal());
				message = Component.translatable("message.overkill.devourer_mode", next.displayName().copy().withStyle(ChatFormatting.GOLD));
			}
			player.displayClientMessage(message, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F,
					0.8F + mode(stack).ordinal() * 0.15F);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.overkill.mode", mode(stack).displayName().copy().withStyle(ChatFormatting.GOLD))
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.overkill.auto_smelt", Tooltips.onOff(autoSmelt(stack))).withStyle(ChatFormatting.GRAY));
		Tooltips.describe(tooltip, "earth_devourer", 4);
	}
}
