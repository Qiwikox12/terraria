package dev.eventhorizon.gametest;

import dev.eventhorizon.EventHorizon;
import dev.eventhorizon.config.GravityConfig;
import dev.eventhorizon.core.Ability;
import dev.eventhorizon.core.ReactorState;
import dev.eventhorizon.item.ModItems;
import dev.eventhorizon.server.GravitySystem;
import dev.eventhorizon.server.GravityTargeting;
import dev.eventhorizon.server.ReactorStore;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GravityGameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void pulseDamagesAndKnocksBackHostilesOnly(TestContext context) {
        Map<BlockPos, BlockState> editedBlocks = new HashMap<>();
        ServerPlayerEntity caster = null;
        ServerPlayerEntity otherPlayer = null;
        boolean previousPassiveSetting = EventHorizon.CONFIG.affectPassiveMobs;
        boolean previousPlayerSetting = EventHorizon.CONFIG.allowPlayerTargets;
        try {
            EventHorizon.CONFIG.affectPassiveMobs = false;
            EventHorizon.CONFIG.allowPlayerTargets = false;
            putFloor(context, editedBlocks, 0, 7, 0, 7);

            caster = gauntletPlayer(context, new Vec3d(1.5, 1, 1.5), -90.0f);
            ReactorState reactor = GravitySystem.reactor(caster);
            reactor.cycleMode();

            ZombieEntity hostile = context.spawnMob(net.minecraft.entity.EntityType.ZOMBIE,
                    new Vec3d(3.5, 1, 1.5));
            SheepEntity passive = context.spawnMob(net.minecraft.entity.EntityType.SHEEP,
                    new Vec3d(1.5, 1, 3.5));
            WolfEntity pet = context.spawnMob(net.minecraft.entity.EntityType.WOLF,
                    new Vec3d(3.5, 1, 3.5));
            pet.setOwner(caster);
            otherPlayer = gauntletPlayer(context, new Vec3d(1.5, 1, 5.5), 0.0f);

            float hostileHealth = hostile.getHealth();
            float passiveHealth = passive.getHealth();
            float petHealth = pet.getHealth();
            float playerHealth = otherPlayer.getHealth();

            context.assertTrue(GravitySystem.cast(caster), "Pulse should cast with nearby valid hostile target");
            context.assertTrue(hostile.getHealth() < hostileHealth, "Pulse should damage a hostile mob");
            context.assertTrue(hostile.getVelocity().x > 0.2, "Pulse should knock a hostile mob away");
            context.assertEquals(passive.getHealth(), passiveHealth, "passive mob health");
            context.assertEquals(pet.getHealth(), petHealth, "tamed pet health");
            context.assertEquals(otherPlayer.getHealth(), playerHealth, "other player health");
        } finally {
            EventHorizon.CONFIG.affectPassiveMobs = previousPassiveSetting;
            EventHorizon.CONFIG.allowPlayerTargets = previousPlayerSetting;
            cleanup(context, editedBlocks, caster, otherPlayer);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void pullCannotTargetThroughSolidWall(TestContext context) {
        Map<BlockPos, BlockState> editedBlocks = new HashMap<>();
        ServerPlayerEntity player = null;
        try {
            putFloor(context, editedBlocks, 0, 7, 0, 7);
            putWallAlongZ(context, editedBlocks, 3, 1, 3, 3, 5);
            player = gauntletPlayer(context, new Vec3d(4.5, 1, 1.5), 0.0f);
            context.spawnMob(net.minecraft.entity.EntityType.ZOMBIE, new Vec3d(4.5, 1, 5.5));
            context.assertTrue(player.getRotationVec(1).z > 0.9, "Pull test player should face toward the wall");

            int energy = GravitySystem.reactor(player).energy();
            context.assertTrue(GravityTargeting.targets(player, GravityTargeting.aim(player), 6, true).isEmpty(),
                    "Solid wall should occlude the target ray");
            context.assertFalse(GravitySystem.cast(player), "Pull should reject a target behind a wall");
            context.assertEquals(GravitySystem.reactor(player).energy(), energy, "energy after wall-blocked cast");
            context.assertEquals(GravitySystem.reactor(player).cooldownRemaining(Ability.PULL, GravitySystem.now(player)),
                    0, "pull cooldown after wall-blocked cast");
        } finally {
            cleanup(context, editedBlocks, player);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void pulseWithoutTargetsDoesNotSpendEnergyOrStartCooldown(TestContext context) {
        ServerPlayerEntity player = null;
        try {
            player = gauntletPlayer(context, new Vec3d(1.5, 1, 1.5), 0.0f);
            ReactorState state = GravitySystem.reactor(player);
            state.cycleMode();
            long now = GravitySystem.now(player);

            context.assertFalse(GravitySystem.cast(player), "Pulse without targets should be rejected");
            context.assertEquals(state.energy(), ReactorState.MAX_ENERGY, "energy after rejected no-target cast");
            context.assertEquals(state.cooldownRemaining(Ability.PULSE, now), 0, "pulse cooldown after rejection");
            context.assertEquals(state.globalCooldownRemaining(now), 0, "global cooldown after rejection");
            context.assertEquals(state.resonance(now), 0, "resonance after rejection");
        } finally {
            cleanup(context, Map.of(), player);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blinkCannotPassThroughSolidWall(TestContext context) {
        Map<BlockPos, BlockState> editedBlocks = new HashMap<>();
        ServerPlayerEntity player = null;
        try {
            putFloor(context, editedBlocks, 2, 6, 0, 14);
            putWallAlongZ(context, editedBlocks, 3, 1, 3, 2, 6);
            player = gauntletPlayer(context, new Vec3d(4.5, 1, 1.5), 0.0f);
            selectBlink(player);
            int energy = GravitySystem.reactor(player).energy();
            context.assertTrue(player.getRotationVec(1).z > 0.9, "Blink test player should face toward the wall");

            BlockPos wall = context.getAbsolutePos(new BlockPos(4, 2, 3));
            context.assertTrue(context.getWorld().getBlockState(wall).isOf(Blocks.STONE),
                    "Wall fixture should be stone at " + wall);
            var destination = GravityTargeting.blinkDestination(player);
            context.assertTrue(destination.isEmpty(), "Blink path should be blocked by the wall; start="
                    + player.getPos() + ", direction=" + player.getRotationVec(1) + ", destination=" + destination);
            context.assertFalse(GravitySystem.cast(player), "Blink should fail when its path crosses a wall");
            context.assertEquals(GravitySystem.reactor(player).energy(), energy, "energy after wall-blocked blink");
            context.assertEquals(GravitySystem.reactor(player).cooldownRemaining(Ability.BLINK, GravitySystem.now(player)),
                    0, "blink cooldown after wall-blocked cast");
        } finally {
            cleanup(context, editedBlocks, player);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blinkCannotLandOnLava(TestContext context) {
        Map<BlockPos, BlockState> editedBlocks = new HashMap<>();
        ServerPlayerEntity player = null;
        try {
            putFloor(context, editedBlocks, 3, 5, 0, 2);
            for (int z = 3; z <= 14; z++) {
                for (int x = 3; x <= 5; x++) {
                    putBlock(context, editedBlocks, x, 0, z, Blocks.LAVA.getDefaultState());
                    for (int y = -1; y >= -3; y--) {
                        putBlock(context, editedBlocks, x, y, z, Blocks.AIR.getDefaultState());
                    }
                }
            }
            player = gauntletPlayer(context, new Vec3d(4.5, 1, 1.5), 0.0f);
            selectBlink(player);
            int energy = GravitySystem.reactor(player).energy();

            context.assertTrue(GravityTargeting.blinkDestination(player).isEmpty(),
                    "Blink should not select lava as landing support");
            context.assertFalse(GravitySystem.cast(player), "Blink should be rejected when all reachable landings are lava");
            context.assertEquals(GravitySystem.reactor(player).energy(), energy, "energy after lava-blocked blink");
        } finally {
            cleanup(context, editedBlocks, player);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blinkCannotLandOverVoid(TestContext context) {
        Map<BlockPos, BlockState> editedBlocks = new HashMap<>();
        ServerPlayerEntity player = null;
        try {
            putFloor(context, editedBlocks, 3, 5, 0, 2);
            for (int z = 3; z <= 14; z++) {
                for (int y = 0; y >= -3; y--) {
                    for (int x = 3; x <= 5; x++) {
                        putBlock(context, editedBlocks, x, y, z, Blocks.AIR.getDefaultState());
                    }
                }
            }
            player = gauntletPlayer(context, new Vec3d(4.5, 1, 1.5), 0.0f);
            selectBlink(player);
            int energy = GravitySystem.reactor(player).energy();

            context.assertTrue(GravityTargeting.blinkDestination(player).isEmpty(),
                    "Blink should have no safe support over the void");
            context.assertFalse(GravitySystem.cast(player), "Blink should be rejected when every landing is void");
            context.assertEquals(GravitySystem.reactor(player).energy(), energy, "energy after void-blocked blink");
        } finally {
            cleanup(context, editedBlocks, player);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blinkReachesValidFloorWithoutEnteringHazard(TestContext context) {
        Map<BlockPos, BlockState> editedBlocks = new HashMap<>();
        ServerPlayerEntity player = null;
        try {
            putFloor(context, editedBlocks, 3, 5, 0, 14);
            player = gauntletPlayer(context, new Vec3d(4.5, 1, 1.5), 0.0f);
            selectBlink(player);
            Vec3d start = player.getPos();
            context.assertTrue(player.getRotationVec(1).z > 0.9, "Blink test player should face along the floor");
            BlockPos farFloor = context.getAbsolutePos(new BlockPos(4, 0, 13));
            context.assertTrue(context.getWorld().getBlockState(farFloor).isOf(Blocks.STONE),
                    "Far floor fixture should be stone at " + farFloor);
            context.assertTrue(context.getWorld().isChunkLoaded(farFloor),
                    "Far floor chunk should be loaded at " + farFloor);
            var destination = GravityTargeting.blinkDestination(player);
            context.assertTrue(destination.isPresent(), "No valid blink destination; start=" + start
                    + ", direction=" + player.getRotationVec(1) + ", farFloor=" + farFloor);

            context.assertTrue(GravitySystem.cast(player), "Blink should reach a valid, loaded floor");
            Vec3d end = player.getPos();
            context.assertTrue(end.z - start.z >= 2.0 && end.z - start.z <= 12.0,
                    "Blink distance should remain within configured bounds");
            BlockPos support = BlockPos.ofFloored(end.add(0, -0.01, 0));
            context.assertTrue(context.getWorld().getBlockState(support).isOf(Blocks.STONE),
                    "Blink destination should be supported by stone");
            context.assertTrue(context.getWorld().isSpaceEmpty(player, player.getBoundingBox()),
                    "Blink destination should not intersect a hazard or solid block");
            context.assertEquals(GravitySystem.reactor(player).energy(), ReactorState.MAX_ENERGY - Ability.BLINK.cost(),
                    "energy after successful blink");
        } finally {
            cleanup(context, editedBlocks, player);
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void reactorStoreNbtRoundTripsEnergyAndCooldownTimers(TestContext context) {
        UUID id = UUID.randomUUID();
        ReactorStore store = ReactorStore.get(context.getWorld().getServer());
        long now = context.getWorld().getServer().getOverworld().getTime();
        ReactorState state = store.reactor(id);
        state.commit(Ability.PULL, now);
        store.markDirty();

        RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
        NbtCompound saved = store.writeNbt(new NbtCompound(), registries);
        ReactorStore loaded = ReactorStore.read(saved.copy(), registries);
        ReactorState restored = loaded.find(id);

        context.assertTrue(restored != null, "Saved player reactor should be present after loading NBT");
        context.assertEquals(restored.energy(), ReactorState.MAX_ENERGY - Ability.PULL.cost(), "round-tripped energy");
        context.assertEquals(restored.cooldownRemaining(Ability.PULL, now), Ability.PULL.cooldownTicks(),
                "round-tripped ability cooldown");
        context.assertEquals(restored.globalCooldownRemaining(now), ReactorState.GLOBAL_COOLDOWN,
                "round-tripped global cooldown");
        context.assertEquals(restored.resonance(now), 1, "round-tripped combo resonance");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void recipesAndItemsAreRegistered(TestContext context) {
        Item[] items = {
                ModItems.GRAVITY_GAUNTLET,
                ModItems.GRAVITY_CORE,
                ModItems.FLUX_CELL,
                ModItems.FIELD_MANUAL
        };
        String[] names = {"gravity_gauntlet", "gravity_core", "flux_cell", "field_manual"};
        for (int i = 0; i < items.length; i++) {
            Identifier id = EventHorizon.id(names[i]);
            context.assertTrue(Registries.ITEM.get(id) == items[i], "Registered item " + id);
            context.assertTrue(context.getWorld().getRecipeManager().get(id).isPresent(), "Loaded recipe " + id);
        }
        context.complete();
    }

    private static ServerPlayerEntity gauntletPlayer(TestContext context, Vec3d relativePosition, float yaw) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d position = context.getAbsolute(relativePosition);
        player.refreshPositionAndAngles(position.x, position.y, position.z, yaw, 0.0f);
        player.setAngles(yaw, 0.0f);
        player.setHeadYaw(yaw);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.GRAVITY_GAUNTLET));
        return player;
    }

    private static void selectBlink(ServerPlayerEntity player) {
        ReactorState state = GravitySystem.reactor(player);
        state.cycleMode();
        state.cycleMode();
    }

    private static void putFloor(TestContext context, Map<BlockPos, BlockState> editedBlocks,
                                 int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                putBlock(context, editedBlocks, x, 0, z, Blocks.STONE.getDefaultState());
            }
        }
    }

    private static void putWall(TestContext context, Map<BlockPos, BlockState> editedBlocks,
                                int x, int minY, int maxY, int minZ, int maxZ) {
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                putBlock(context, editedBlocks, x, y, z, Blocks.STONE.getDefaultState());
            }
        }
    }

    private static void putWallAlongZ(TestContext context, Map<BlockPos, BlockState> editedBlocks,
                                      int z, int minY, int maxY, int minX, int maxX) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                putBlock(context, editedBlocks, x, y, z, Blocks.STONE.getDefaultState());
            }
        }
    }

    private static void putBlock(TestContext context, Map<BlockPos, BlockState> editedBlocks,
                                 int x, int y, int z, BlockState state) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(x, y, z));
        editedBlocks.putIfAbsent(pos, context.getWorld().getBlockState(pos));
        context.getWorld().setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    private static void cleanup(TestContext context, Map<BlockPos, BlockState> editedBlocks,
                                ServerPlayerEntity... players) {
        context.killAllEntities();
        for (ServerPlayerEntity player : players) {
            if (player != null) {
                player.networkHandler.disconnect(Text.literal("GameTest cleanup"));
            }
        }
        editedBlocks.forEach((pos, state) -> context.getWorld().setBlockState(pos, state, Block.NOTIFY_ALL));
    }
}
