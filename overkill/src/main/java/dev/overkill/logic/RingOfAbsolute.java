package dev.overkill.logic;

import dev.overkill.item.RingOfAbsoluteItem;
import dev.overkill.network.RevivePayload;
import dev.overkill.registry.ModAttachments;
import dev.overkill.registry.ModDamageTypes;
import dev.overkill.registry.ModItems;
import dev.overkill.util.Advancements;
import dev.overkill.util.Fx;
import dev.overkill.util.Targeting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Passive powers of the Ring of the Absolute, active while it sits anywhere in the player's inventory. */
public final class RingOfAbsolute {
	public static final int REVIVE_COOLDOWN = 2400;
	public static final double MAGNET_RADIUS = 10.0;
	private static final int EFFECT_DURATION = 400;
	/** Refresh before Night Vision drops under 200 ticks, where vanilla starts flickering the screen. */
	private static final int EFFECT_REFRESH_BELOW = 300;

	private record Buff(Holder<MobEffect> effect, int amplifier) {
	}

	private static final List<Buff> BUFFS = List.of(
			new Buff(MobEffects.NIGHT_VISION, 0),
			new Buff(MobEffects.FIRE_RESISTANCE, 0),
			new Buff(MobEffects.WATER_BREATHING, 0),
			new Buff(MobEffects.DIG_SPEED, 2),
			new Buff(MobEffects.DAMAGE_RESISTANCE, 1),
			new Buff(MobEffects.REGENERATION, 1));

	private RingOfAbsolute() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(RingOfAbsolute::allowDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && !findRing(player).isEmpty()));
	}

	public static ItemStack findRing(Player player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.RING_OF_ABSOLUTE)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void tick(ServerPlayer player) {
		ItemStack ring = player.isSpectator() ? ItemStack.EMPTY : findRing(player);
		boolean has = !ring.isEmpty();
		updateFlight(player, has);
		if (!has || !player.isAlive()) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			for (Buff buff : BUFFS) {
				MobEffectInstance current = player.getEffect(buff.effect);
				if (current == null || current.getAmplifier() < buff.amplifier
						|| current.getAmplifier() == buff.amplifier && !current.isInfiniteDuration() && current.getDuration() < EFFECT_REFRESH_BELOW) {
					player.addEffect(new MobEffectInstance(buff.effect, EFFECT_DURATION, buff.amplifier, true, false, true));
				}
			}
		}
		FoodData food = player.getFoodData();
		if (food.needsFood() || food.getSaturationLevel() < 5.0F) {
			food.setFoodLevel(20);
			food.setSaturation(20.0F);
		}
		if (RingOfAbsoluteItem.magnet(ring)) {
			magnet(player);
		}
	}

	private static void updateFlight(ServerPlayer player, boolean has) {
		Abilities abilities = player.getAbilities();
		boolean granted = player.getAttachedOrElse(ModAttachments.RING_FLIGHT, false);
		if (has) {
			if (!abilities.mayfly) {
				abilities.mayfly = true;
				player.onUpdateAbilities();
				player.setAttached(ModAttachments.RING_FLIGHT, true);
			}
		} else if (granted) {
			player.removeAttached(ModAttachments.RING_FLIGHT);
			if (!player.isCreative() && !player.isSpectator()) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
		}
	}

	private static void magnet(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(MAGNET_RADIUS);
		Vec3 target = player.position().add(0.0, 0.75, 0.0);
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box,
				i -> i.isAlive() && !i.hasPickUpDelay() && i.getOwner() != player)) {
			pullTowards(item, target);
		}
		for (ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive)) {
			pullTowards(orb, target);
		}
	}

	private static void pullTowards(Entity entity, Vec3 target) {
		Vec3 delta = target.subtract(entity.position());
		double distance = delta.length();
		if (distance < 0.4) {
			return;
		}
		entity.setDeltaMovement(delta.scale(1.0 / distance).scale(Math.min(0.7, 0.2 + distance * 0.06)));
		entity.hurtMarked = true;
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypes.GENERIC_KILL)) {
			return true;
		}
		ItemStack ring = findRing(player);
		if (ring.isEmpty() || player.getCooldowns().isOnCooldown(ModItems.RING_OF_ABSOLUTE)) {
			return true;
		}
		revive(player, ring, source);
		return false;
	}

	private static void revive(ServerPlayer player, ItemStack ring, DamageSource source) {
		ServerLevel level = player.serverLevel();
		player.setHealth(player.getMaxHealth());
		List<Holder<MobEffect>> harmful = new ArrayList<>();
		for (MobEffectInstance instance : player.getActiveEffects()) {
			if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				harmful.add(instance.getEffect());
			}
		}
		harmful.forEach(player::removeEffect);
		player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 20.0F));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
		player.clearFire();
		player.resetFallDistance();

		if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || player.getY() < level.getMinBuildHeight()) {
			rescueFromVoid(player, level);
			level = player.serverLevel();
		}
		player.getCooldowns().addCooldown(ModItems.RING_OF_ABSOLUTE, REVIVE_COOLDOWN);

		DamageSource wrath = ModDamageTypes.source(level, ModDamageTypes.ABSOLUTE_WRATH, player);
		Vec3 c = player.position().add(0.0, 1.0, 0.0);
		for (LivingEntity victim : Targeting.around(level, player, c, 8.0, false)) {
			victim.hurt(wrath, 40.0F);
			Vec3 away = victim.position().subtract(player.position());
			if (away.lengthSqr() > 1.0E-4) {
				victim.setDeltaMovement(away.normalize().scale(2.0).add(0.0, 0.6, 0.0));
				victim.hurtMarked = true;
			}
		}
		// Retribution: whoever landed the killing blow gets hit even if they are out of the shockwave.
		if (source.getEntity() instanceof LivingEntity killer && killer != player && killer.level() == level
				&& killer.distanceToSqr(player) < 64.0 * 64.0 && Targeting.canHarm(player, killer, true)) {
			killer.hurt(wrath, 40.0F);
			Fx.line(level, ParticleTypes.END_ROD, c, killer.getBoundingBox().getCenter(), 0.5, 1, 0.02);
		}

		Fx.particle(level, ParticleTypes.TOTEM_OF_UNDYING, c, 150, 0.5, 0.9);
		Fx.particle(level, ParticleTypes.FLASH, c, 1, 0.0, 0.0);
		Fx.sphere(level, ParticleTypes.END_ROD, c, 3.0, 80);
		Fx.sound(level, c, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		Fx.sound(level, c, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.2F);
		Fx.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.5F);

		if (ServerPlayNetworking.canSend(player, RevivePayload.TYPE)) {
			ServerPlayNetworking.send(player, new RevivePayload(ring.copyWithCount(1)));
		}
		player.displayClientMessage(Component.translatable("message.overkill.second_life").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		Advancements.grant(player, "second_life");
	}

	private static void rescueFromVoid(ServerPlayer player, ServerLevel level) {
		int x = Mth.floor(player.getX());
		int z = Mth.floor(player.getZ());
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		player.setDeltaMovement(Vec3.ZERO);
		if (surface > level.getMinBuildHeight() + 1) {
			player.teleportTo(level, x + 0.5, surface + 0.1, z + 0.5, player.getYRot(), player.getXRot());
		} else {
			ServerLevel overworld = player.server.overworld();
			BlockPos spawn = overworld.getSharedSpawnPos();
			int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
			player.teleportTo(overworld, spawn.getX() + 0.5, y + 0.1, spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
		}
		player.resetFallDistance();
		player.hurtMarked = true;
	}
}
