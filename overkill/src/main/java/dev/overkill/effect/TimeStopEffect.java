package dev.overkill.effect;

import dev.overkill.network.TimeStopPayload;
import dev.overkill.util.Advancements;
import dev.overkill.util.Fx;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Freezes every entity around the caster: mobs lose AI, projectiles and TNT hang in the air, other players are locked
 * in place. Damage dealt to frozen targets is stored and lands all at once when time resumes.
 */
public class TimeStopEffect implements WorldEffect {
	public static final int DURATION = 200;
	public static final double RADIUS = 48.0;
	/** Projectiles fired by the caster fly this many ticks before freezing, so they stop just past the hand. */
	private static final int PROJECTILE_GRACE = 3;
	// Tags make freezing crash-safe: if the server dies mid-effect, entities are thawed when they next load.
	private static final String TAG_AI = "overkill_frozen_ai";
	private static final String TAG_GRAVITY = "overkill_frozen_gravity";

	private final UUID owner;
	private final Map<UUID, Frozen> frozen = new LinkedHashMap<>();
	private Vec3 center;
	private int age;
	private boolean released;

	public TimeStopEffect(ServerPlayer caster) {
		this.owner = caster.getUUID();
		this.center = caster.position();
	}

	private static final class Frozen {
		final Entity entity;
		final boolean player;
		final Vec3 pos;
		final Vec3 velocity;
		final float yRot;
		final float xRot;
		final float headRot;
		final float bodyRot;
		boolean disabledAi;
		boolean addedNoGravity;
		int fuse = -1;
		int fallingTime = -1;
		float pendingDamage;
		int hits;
		@Nullable
		DamageSource lastSource;

		Frozen(Entity entity) {
			this.entity = entity;
			this.player = entity instanceof Player;
			this.pos = entity.position();
			this.velocity = entity.getDeltaMovement();
			this.yRot = entity.getYRot();
			this.xRot = entity.getXRot();
			this.headRot = entity.getYHeadRot();
			this.bodyRot = entity instanceof LivingEntity living ? living.yBodyRot : 0.0F;
		}
	}

	public static void registerHooks() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TimeStopEffect::allowDamage);
		ServerEntityEvents.ENTITY_LOAD.register(TimeStopEffect::thawOrphan);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isFrozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> isFrozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> isFrozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isFrozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> isFrozen(player)
				? InteractionResultHolder.fail(player.getItemInHand(hand))
				: InteractionResultHolder.pass(ItemStack.EMPTY));
	}

	@Nullable
	private static Frozen lookup(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		for (TimeStopEffect effect : WorldEffects.active(level, TimeStopEffect.class)) {
			if (!effect.released) {
				Frozen f = effect.frozen.get(entity.getUUID());
				if (f != null) {
					return f;
				}
			}
		}
		return null;
	}

	public static boolean isFrozen(Entity entity) {
		return lookup(entity) != null;
	}

	public static boolean isCaster(ServerLevel level, Player player) {
		for (TimeStopEffect effect : WorldEffects.active(level, TimeStopEffect.class)) {
			if (effect.owner.equals(player.getUUID()) && !effect.released) {
				return true;
			}
		}
		return false;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		Frozen f = lookup(entity);
		if (f == null) {
			return true;
		}
		f.pendingDamage += amount;
		f.hits++;
		f.lastSource = source;
		if (entity.level() instanceof ServerLevel level) {
			Vec3 c = entity.getBoundingBox().getCenter();
			Fx.particle(level, ParticleTypes.ENCHANTED_HIT, c, 10, 0.3, 0.3);
			Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, Math.min(2.0F, 0.6F + f.hits * 0.08F));
		}
		return false;
	}

	private static void thawOrphan(Entity entity, ServerLevel level) {
		boolean ai = entity.getTags().contains(TAG_AI);
		boolean gravity = entity.getTags().contains(TAG_GRAVITY);
		if ((ai || gravity) && !isFrozen(entity)) {
			if (ai && entity instanceof Mob mob) {
				mob.setNoAi(false);
			}
			if (gravity) {
				entity.setNoGravity(false);
			}
			entity.removeTag(TAG_AI);
			entity.removeTag(TAG_GRAVITY);
		}
	}

	@Override
	public boolean tick(ServerLevel level) {
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(owner);
		if (caster == null || caster.level() != level || !caster.isAlive()) {
			return false;
		}
		center = caster.position();
		if (age == 0) {
			start(level, caster);
		}
		age++;
		if (age >= DURATION) {
			return false;
		}
		freezeNewcomers(level);
		hold();
		ambience(level, caster);
		return true;
	}

	@Override
	public void end(ServerLevel level, boolean interrupted) {
		release(level);
	}

	private void start(ServerLevel level, ServerPlayer caster) {
		Vec3 eye = caster.getEyePosition();
		Fx.sound(level, eye, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 4.0F, 0.6F);
		Fx.sound(level, eye, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4.0F, 0.5F);
		Fx.sound(level, eye, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.5F);
		Fx.particle(level, ParticleTypes.FLASH, eye, 1, 0.0, 0.0);
		Fx.sphere(level, ParticleTypes.END_ROD, eye, 4.0, 90);
		caster.displayClientMessage(Component.translatable("message.overkill.time_stop").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
		Advancements.grant(caster, "za_warudo");
		broadcast(level, true);
	}

	private void broadcast(ServerLevel level, boolean active) {
		TimeStopPayload payload = new TimeStopPayload(active, center, (float) RADIUS, Math.max(0, DURATION - age));
		for (ServerPlayer player : level.players()) {
			if (ServerPlayNetworking.canSend(player, TimeStopPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private void freezeNewcomers(ServerLevel level) {
		double r2 = RADIUS * RADIUS;
		List<Entity> candidates = level.getEntities((Entity) null, new AABB(center, center).inflate(RADIUS),
				e -> e.position().distanceToSqr(center) <= r2);
		for (Entity e : candidates) {
			if (e.getUUID().equals(owner) || frozen.containsKey(e.getUUID()) || e.isRemoved() || e.isSpectator()
					|| e instanceof LightningBolt || e instanceof EnderDragonPart || isFrozen(e)) {
				continue;
			}
			if (e instanceof Player player && player.isCreative()) {
				continue;
			}
			if (e instanceof Projectile projectile && projectile.getOwner() != null
					&& projectile.getOwner().getUUID().equals(owner) && projectile.tickCount < PROJECTILE_GRACE) {
				continue;
			}
			freeze(e);
		}
	}

	private void freeze(Entity e) {
		Frozen f = new Frozen(e);
		if (!f.player) {
			if (e instanceof Mob mob && !mob.isNoAi()) {
				mob.setNoAi(true);
				f.disabledAi = true;
				e.addTag(TAG_AI);
			}
			if (!e.isNoGravity()) {
				e.setNoGravity(true);
				f.addedNoGravity = true;
				e.addTag(TAG_GRAVITY);
			}
			if (e instanceof PrimedTnt tnt) {
				f.fuse = tnt.getFuse();
			}
			if (e instanceof FallingBlockEntity falling) {
				f.fallingTime = falling.time;
			}
			e.setDeltaMovement(Vec3.ZERO);
			e.hurtMarked = true;
		}
		frozen.put(e.getUUID(), f);
	}

	private void hold() {
		int remaining = DURATION - age + 5;
		frozen.values().removeIf(f -> f.entity.isRemoved());
		for (Frozen f : frozen.values()) {
			Entity e = f.entity;
			if (f.player) {
				ServerPlayer player = (ServerPlayer) e;
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, remaining, 9, false, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, remaining, 9, false, false, false));
				if (player.position().distanceToSqr(f.pos) > 0.09) {
					player.connection.teleport(f.pos.x, f.pos.y, f.pos.z, player.getYRot(), player.getXRot());
				}
				continue;
			}
			e.setDeltaMovement(Vec3.ZERO);
			if (!e.isPassenger() && e.position().distanceToSqr(f.pos) > 1.0E-6) {
				e.setPos(f.pos);
			}
			e.setYRot(f.yRot);
			e.setXRot(f.xRot);
			e.setYHeadRot(f.headRot);
			if (e instanceof LivingEntity living) {
				living.yBodyRot = f.bodyRot;
			}
			if (e instanceof PrimedTnt tnt) {
				tnt.setFuse(f.fuse);
			}
			if (e instanceof FallingBlockEntity falling) {
				falling.time = f.fallingTime;
			}
		}
	}

	private void ambience(ServerLevel level, ServerPlayer caster) {
		if (age % 20 == 0) {
			Fx.sound(level, caster.position(), SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, 1.2F, (age / 20) % 2 == 0 ? 1.9F : 1.5F);
		}
		if (age % 10 == 0) {
			broadcast(level, true);
		}
		int remaining = DURATION - age;
		if (remaining == 60 || remaining == 40 || remaining == 20) {
			caster.displayClientMessage(Component.translatable("message.overkill.time_resumes", remaining / 20).withStyle(ChatFormatting.AQUA), true);
		}
	}

	private void release(ServerLevel level) {
		if (released) {
			return;
		}
		released = true;
		int victims = 0;
		for (Frozen f : frozen.values()) {
			Entity e = f.entity;
			if (e.isRemoved()) {
				continue;
			}
			if (f.player) {
				ServerPlayer player = (ServerPlayer) e;
				player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
				player.removeEffect(MobEffects.DIG_SLOWDOWN);
			} else {
				if (f.disabledAi && e instanceof Mob mob) {
					mob.setNoAi(false);
				}
				if (f.addedNoGravity) {
					e.setNoGravity(false);
				}
				e.removeTag(TAG_AI);
				e.removeTag(TAG_GRAVITY);
				e.setDeltaMovement(f.velocity);
				e.hurtMarked = true;
			}
			if (f.pendingDamage > 0.0F && e instanceof LivingEntity living) {
				DamageSource source = f.lastSource != null ? f.lastSource : level.damageSources().magic();
				living.invulnerableTime = 0;
				living.hurt(source, f.pendingDamage);
				Vec3 c = living.getBoundingBox().getCenter();
				Fx.particle(level, ParticleTypes.CRIT, c, Math.min(80, 10 + f.hits * 6), 0.4, 0.6);
				Fx.particle(level, ParticleTypes.DAMAGE_INDICATOR, c, Math.min(30, 3 + f.hits * 2), 0.4, 0.2);
				victims++;
			}
		}
		frozen.clear();

		Vec3 eye = center.add(0.0, 1.6, 0.0);
		Fx.sound(level, eye, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0F, 0.7F);
		Fx.sound(level, eye, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0F, 0.5F);
		if (victims > 0) {
			Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 3.0F, 0.6F);
		}
		Fx.sphere(level, ParticleTypes.REVERSE_PORTAL, eye, 3.0, 70);
		if (level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer caster && caster.level() == level) {
			caster.displayClientMessage(Component.translatable("message.overkill.time_resumed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
		}
		broadcast(level, false);
	}

	/** Test hook: how much damage is queued on an entity. */
	public static float pendingDamage(Entity entity) {
		Frozen f = lookup(entity);
		return f == null ? 0.0F : f.pendingDamage;
	}

	public static boolean inZone(Level level, Vec3 pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		for (TimeStopEffect effect : WorldEffects.active(serverLevel, TimeStopEffect.class)) {
			if (!effect.released && effect.center.distanceToSqr(pos) <= RADIUS * RADIUS) {
				return true;
			}
		}
		return false;
	}
}
