package dev.overkill.item;

import dev.overkill.effect.OrbitalStrikeEffect;
import dev.overkill.effect.WorldEffects;
import dev.overkill.registry.ModComponents;
import dev.overkill.util.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class OrbitalDesignatorItem extends Item {
	public static final double RANGE = 192.0;
	public static final int COOLDOWN = 160;

	public OrbitalDesignatorItem(Properties properties) {
		super(properties);
	}

	public static boolean surgical(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SURGICAL, false);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				boolean surgical = !surgical(stack);
				stack.set(ModComponents.SURGICAL, surgical);
				player.displayClientMessage(Component.translatable(surgical ? "message.overkill.surgical" : "message.overkill.devastation")
						.withStyle(surgical ? ChatFormatting.AQUA : ChatFormatting.RED), true);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F,
						surgical ? 1.4F : 0.7F);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (level instanceof ServerLevel serverLevel) {
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
			BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			Vec3 blockEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

			// Prefer a living target in the crosshair: the strike then tracks it until impact.
			EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, blockEnd,
					new AABB(eye, blockEnd).inflate(1.0), e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator(),
					eye.distanceToSqr(blockEnd));

			Entity tracked = entityHit != null ? entityHit.getEntity() : null;
			if (tracked == null && blockHit.getType() == HitResult.Type.MISS) {
				player.displayClientMessage(Component.translatable("message.overkill.no_target").withStyle(ChatFormatting.RED), true);
				return InteractionResultHolder.fail(stack);
			}
			Vec3 target = tracked != null ? tracked.position() : blockHit.getLocation();
			WorldEffects.add(serverLevel, new OrbitalStrikeEffect(player, target, tracked, surgical(stack)));
			player.displayClientMessage(Component.translatable(tracked != null ? "message.overkill.strike_locked" : "message.overkill.strike_inbound",
					tracked != null ? tracked.getDisplayName() : Component.empty()).withStyle(ChatFormatting.GOLD), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		}
		player.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.overkill.mode",
				Component.translatable(surgical(stack) ? "mode.overkill.surgical" : "mode.overkill.devastation")
						.withStyle(surgical(stack) ? ChatFormatting.AQUA : ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
		Tooltips.describe(tooltip, "orbital_designator", 4);
	}
}
