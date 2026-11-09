package dev.overkill.test;

import com.mojang.authlib.GameProfile;
import dev.overkill.effect.BlackHoleEffect;
import dev.overkill.effect.OrbitalStrikeEffect;
import dev.overkill.effect.TimeAccelerationEffect;
import dev.overkill.effect.TimeStopEffect;
import dev.overkill.effect.WorldEffects;
import dev.overkill.item.DevourerMode;
import dev.overkill.logic.DevourerMining;
import dev.overkill.logic.GravityGrab;
import dev.overkill.logic.VoidReaverLogic;
import dev.overkill.registry.ModComponents;
import dev.overkill.registry.ModGameRules;
import dev.overkill.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side behaviour checks for every artifact. Each test sits in its own batch because the effects are huge
 * (48-block time stop, 16-block orbital strike) and would otherwise bleed into neighbouring tests.
 */
public class OverkillGameTests implements FabricGameTest {

	/** The vanilla mock player is hard-wired to creative, which skips drops; this one is a real survival player. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "overkill-test");
		ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
		// The game test world defaults to creative.
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void place(GameTestHelper helper, ServerPlayer player, double x, double y, double z, float yRot) {
		Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
		player.teleportTo(helper.getLevel(), pos.x, pos.y, pos.z, yRot, 0.0F);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "devourer_area")
	public void devourerMinesCubeIntoInventory(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		place(helper, player, 3.5, 6, 0.5, 0.0F);
		ItemStack tool = new ItemStack(ModItems.EARTH_DEVOURER);
		tool.set(ModComponents.DEVOURER_MODE, DevourerMode.CUBE_3.ordinal());
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);

		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 2; z <= 4; z++) {
					helper.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(3, 3, 3));
		int broken = DevourerMining.mine(player, helper.getLevel(), origin, Direction.UP, tool);

		helper.assertTrue(broken == 27, "expected 27 blocks broken, got " + broken);
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 2; z <= 4; z++) {
					helper.assertBlockPresent(Blocks.AIR, x, y, z);
				}
			}
		}
		int cobble = player.getInventory().countItem(Items.COBBLESTONE);
		helper.assertTrue(cobble == 27, "expected 27 cobblestone in inventory, got " + cobble);
		player.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "devourer_smelt")
	public void devourerAutoSmeltsVeins(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		place(helper, player, 0.5, 6, 0.5, 0.0F);
		ItemStack tool = new ItemStack(ModItems.EARTH_DEVOURER);
		tool.set(ModComponents.DEVOURER_MODE, DevourerMode.VEIN.ordinal());
		tool.set(ModComponents.AUTO_SMELT, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);

		// An L-shaped vein of 5 iron ore next to a stone block that must survive.
		helper.setBlock(2, 1, 2, Blocks.IRON_ORE);
		helper.setBlock(3, 1, 2, Blocks.IRON_ORE);
		helper.setBlock(4, 1, 2, Blocks.IRON_ORE);
		helper.setBlock(4, 2, 2, Blocks.IRON_ORE);
		helper.setBlock(4, 3, 3, Blocks.IRON_ORE);
		helper.setBlock(1, 1, 2, Blocks.STONE);

		int broken = DevourerMining.mine(player, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, tool);
		helper.assertTrue(broken == 5, "expected the 5-block vein, got " + broken);
		helper.assertBlockPresent(Blocks.STONE, 1, 1, 2);
		int ingots = player.getInventory().countItem(Items.IRON_INGOT);
		int raw = player.getInventory().countItem(Items.RAW_IRON);
		helper.assertTrue(ingots == 5 && raw == 0, "expected 5 smelted ingots, got ingots=" + ingots + " raw=" + raw);
		helper.assertTrue(player.totalExperience > 0, "smelting should grant XP");
		player.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "orbital", timeoutTicks = 200)
	public void orbitalStrikeObliteratesTargetsButSparesOwner(GameTestHelper helper) {
		floor(helper, 8);
		ServerPlayer owner = survivalPlayer(helper);
		place(helper, owner, 4.5, 1, 4.5, 0.0F);
		Zombie a = helper.spawn(EntityType.ZOMBIE, new Vec3(1.5, 1, 1.5));
		Zombie b = helper.spawn(EntityType.ZOMBIE, new Vec3(7.5, 1, 7.5));
		Zombie c = helper.spawn(EntityType.ZOMBIE, new Vec3(1.5, 1, 7.5));
		for (Zombie z : new Zombie[] {a, b, c}) {
			z.setNoAi(true);
		}
		Vec3 target = helper.absoluteVec(new Vec3(4.5, 1, 4.5));
		WorldEffects.add(helper.getLevel(), new OrbitalStrikeEffect(owner, target, null, true));

		helper.runAfterDelay(OrbitalStrikeEffect.CHARGE_TICKS - 5, () -> helper.assertTrue(a.isAlive(), "strike landed early"));
		helper.runAfterDelay(OrbitalStrikeEffect.CHARGE_TICKS + 5, () -> {
			helper.assertTrue(!a.isAlive() && !b.isAlive() && !c.isAlive(), "all zombies in the blast radius must die");
			helper.assertTrue(owner.isAlive() && owner.getHealth() == owner.getMaxHealth(), "owner must be unharmed, health=" + owner.getHealth());
			helper.assertBlockPresent(Blocks.STONE, 4, 0, 4);
			owner.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "time_stop", timeoutTicks = 300)
	public void timeStopFreezesAndReleasesStoredDamage(GameTestHelper helper) {
		floor(helper, 8);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		place(helper, caster, 1.5, 1, 1.5, 0.0F);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(5.5, 4, 5.5));
		zombie.setDeltaMovement(0.0, 0.8, 0.0);
		WorldEffects.add(helper.getLevel(), new TimeStopEffect(caster));

		Vec3[] frozenAt = new Vec3[1];
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(zombie.isNoAi(), "zombie AI should be suspended");
			helper.assertTrue(TimeStopEffect.isFrozen(zombie), "zombie should be frozen");
			frozenAt[0] = zombie.position();
			float before = zombie.getHealth();
			zombie.hurt(helper.getLevel().damageSources().playerAttack(caster), 6.0F);
			zombie.invulnerableTime = 0;
			zombie.hurt(helper.getLevel().damageSources().playerAttack(caster), 5.0F);
			helper.assertTrue(zombie.getHealth() == before, "damage must be deferred while frozen");
			helper.assertTrue(TimeStopEffect.pendingDamage(zombie) == 11.0F, "pending damage should be 11, got " + TimeStopEffect.pendingDamage(zombie));
		});
		helper.runAfterDelay(60, () -> helper.assertTrue(zombie.position().distanceTo(frozenAt[0]) < 1.0E-3,
				"frozen zombie must not move or fall, moved " + zombie.position().distanceTo(frozenAt[0])));
		helper.runAfterDelay(TimeStopEffect.DURATION + 5, () -> {
			helper.assertFalse(TimeStopEffect.isFrozen(zombie), "zombie should be released");
			helper.assertFalse(zombie.isNoAi(), "AI must be restored");
			helper.assertFalse(zombie.isNoGravity(), "gravity must be restored");
			helper.assertTrue(zombie.getHealth() <= zombie.getMaxHealth() - 11.0F + 0.01F,
					"stored damage must land on release, health=" + zombie.getHealth());
			caster.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "ring")
	public void ringCheatsDeathAndRescuesFromVoid(GameTestHelper helper) {
		floor(helper, 4);
		ServerPlayer player = survivalPlayer(helper);
		place(helper, player, 1.5, 1, 1.5, 0.0F);
		player.getInventory().add(new ItemStack(ModItems.RING_OF_ABSOLUTE));

		player.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1000.0F);
		helper.assertTrue(player.isAlive(), "ring should prevent death");
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "player should be fully healed, health=" + player.getHealth());
		helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.RING_OF_ABSOLUTE), "second life must go on cooldown");

		// Damage immunity from the first hit would otherwise swallow the second one.
		player.invulnerableTime = 0;
		player.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1000.0F);
		helper.assertTrue(player.isDeadOrDying(), "a second lethal hit during cooldown must kill");
		player.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "ring_flight", timeoutTicks = 60)
	public void ringGrantsAndRevokesFlight(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		place(helper, player, 1.5, 3, 1.5, 0.0F);
		player.getInventory().add(new ItemStack(ModItems.RING_OF_ABSOLUTE));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(player.getAbilities().mayfly, "ring should grant flight");
			player.getInventory().clearContent();
		});
		helper.runAfterDelay(4, () -> {
			helper.assertFalse(player.getAbilities().mayfly, "removing the ring should revoke flight");
			player.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "black_hole", timeoutTicks = 100)
	public void blackHolePullsEverythingIn(GameTestHelper helper) {
		helper.getLevel().getGameRules().getRule(ModGameRules.GRIEFING).set(false, helper.getLevel().getServer());
		floor(helper, 8);
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(7.5, 1, 4.5));
		Vec3 center = helper.absoluteVec(new Vec3(0.5, 3, 4.5));
		double start = pig.position().distanceTo(center);
		WorldEffects.add(helper.getLevel(), new BlackHoleEffect(center, null));
		helper.runAfterDelay(25, () -> {
			double now = pig.getBoundingBox().getCenter().distanceTo(center);
			helper.assertTrue(now < 2.5, "pig should be dragged into the core (start " + start + ", now " + now + ")");
			helper.getLevel().getGameRules().getRule(ModGameRules.GRIEFING).set(true, helper.getLevel().getServer());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "chronos", timeoutTicks = 100)
	public void chronosAcceleratesFurnaces(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, Blocks.FURNACE);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		FurnaceBlockEntity furnace = (FurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
		helper.assertTrue(furnace != null, "furnace block entity missing");
		furnace.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		furnace.setItem(1, new ItemStack(Items.COAL, 1));
		TimeAccelerationEffect.start(helper.getLevel(), pos);
		// Vanilla needs 200 ticks per item; at 16x the furnace should finish at least one within 40.
		helper.runAfterDelay(40, () -> {
			int out = furnace.getItem(2).getCount();
			helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT) && out >= 2, "expected >= 2 ingots after 40 ticks, got " + furnace.getItem(2));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "void_reaver")
	public void voidStepDashesThroughEnemies(GameTestHelper helper) {
		floor(helper, 8);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// yRot -90 faces +X. The test area is walled with barriers at x = 8, so the dash must stop short of it.
		place(helper, player, 0.5, 1, 4.5, -90.0F);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5, 1, 4.5));
		zombie.setNoAi(true);
		double startX = player.getX();
		double wallX = helper.absoluteVec(new Vec3(8.0, 0, 0)).x;

		boolean moved = VoidReaverLogic.voidStep(helper.getLevel(), player);
		helper.assertTrue(moved, "dash should succeed in open space");
		helper.assertTrue(player.getX() - startX > 5.0, "player should dash along +X, moved " + (player.getX() - startX));
		helper.assertTrue(player.getBoundingBox().maxX <= wallX, "dash must not clip into the wall");
		helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "zombie on the path must be hit");
		player.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "gauntlet", timeoutTicks = 60)
	public void gauntletGrabsAndThrows(GameTestHelper helper) {
		floor(helper, 8);
		ServerPlayer player = survivalPlayer(helper);
		place(helper, player, 1.5, 1, 4.5, -90.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GRAVITY_GAUNTLET));
		// Eye height is ~2.62 here; lift the pig into the crosshair.
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(5.5, 1.8, 4.5));

		helper.assertTrue(GravityGrab.grab(helper.getLevel(), player), "should grab the pig in the crosshair");
		helper.assertTrue(GravityGrab.heldBy(player) == pig, "held entity should be the pig");
		helper.assertTrue(pig.isNoGravity(), "held entity floats");
		helper.runAfterDelay(10, () -> {
			double distance = pig.distanceTo(player);
			helper.assertTrue(distance < 6.0 && pig.getY() > player.getY() + 0.5, "pig should hover in front of the player, distance " + distance);
			helper.assertTrue(GravityGrab.launch(player), "launch should succeed");
			helper.assertFalse(GravityGrab.isHolding(player), "launch releases the grab");
		});
		// Thrown straight into the barrier wall: the impact must hurt it.
		helper.runAfterDelay(16, () -> {
			helper.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "thrown pig should take impact damage");
			helper.assertFalse(pig.isNoGravity(), "gravity must be restored after impact");
			player.discard();
			helper.succeed();
		});
	}
}
