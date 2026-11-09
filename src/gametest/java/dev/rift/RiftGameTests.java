package dev.rift;

import dev.rift.net.PowerPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RiftGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_targets")
    public void protectsPlayersPetsVillagersAndNamedMobs(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        var wolf = EntityType.WOLF.create(context.getWorld());
        wolf.setTamed(true, true);
        context.assertFalse(Targets.canAffect(player), "Players must never be targets");
        context.assertFalse(Targets.canAffect(wolf), "Tamed pets must never be targets");
        context.assertFalse(Targets.canAffect(EntityType.VILLAGER.create(context.getWorld())), "Villagers are protected");
        context.assertFalse(Targets.canAffect(EntityType.IRON_GOLEM.create(context.getWorld())), "Golems are protected");
        context.assertFalse(Targets.canAffect(EntityType.ARMOR_STAND.create(context.getWorld())), "Armor stands are protected");
        var named = EntityType.HUSK.create(context.getWorld());
        named.setCustomName(Text.literal("Keep me"));
        context.assertFalse(Targets.canAffect(named), "Named hostile mobs are protected");
        context.assertFalse(Targets.canAffect(EntityType.WITHER.create(context.getWorld())), "Vanilla bosses are excluded");
        context.assertFalse(Targets.canAffect(EntityType.ENDER_DRAGON.create(context.getWorld())), "Dragons are excluded");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_monsters")
    public void supportsDifferentHostileMobFamilies(TestContext context) {
        for (var type : List.of(EntityType.HUSK, EntityType.SLIME, EntityType.GHAST, EntityType.PHANTOM)) {
            context.assertTrue(Targets.canAffect(type.create(context.getWorld())), "Hostile family excluded: " + type);
        }
        context.assertFalse(Targets.canAffect(EntityType.COW.create(context.getWorld())), "Passive mobs are safe by default");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_wave")
    public void shockwaveDamagesHostilesWithoutGriefing(TestContext context) {
        floor(context);
        PlayerEntity owner = context.createMockPlayer(GameMode.SURVIVAL);
        HuskEntity enemy = husk(context, 3, 1, 3);
        var villager = context.spawnEntity(EntityType.VILLAGER, 4, 1, 3);
        Vec3d center = context.getAbsolute(new Vec3d(2, 1.5, 3));
        int hits = Targets.shockwave(context.getWorld(), center, owner, 3, 8, 1.2, Set.of());
        context.assertEquals(1, hits, "Only the hostile mob should be hit");
        context.assertTrue(enemy.getHealth() < 100, "Enemy must take damage");
        context.assertEquals(villager.getMaxHealth(), villager.getHealth(), "Villager was damaged");
        context.assertTrue(enemy.getVelocity().lengthSquared() > 0, "Enemy must be knocked back");
        context.assertTrue(context.getBlockState(new BlockPos(3, 0, 3)).isOf(Blocks.STONE), "Floor was destroyed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_wall")
    public void wavesRespectSolidWalls(TestContext context) {
        floor(context);
        for (int y = 1; y < 5; y++) for (int z = 0; z < 8; z++) context.setBlockState(3, y, z, Blocks.OBSIDIAN);
        HuskEntity enemy = husk(context, 5, 1, 3);
        PlayerEntity owner = context.createMockPlayer(GameMode.SURVIVAL);
        int hits = Targets.shockwave(context.getWorld(), context.getAbsolute(new Vec3d(1, 2, 3)), owner,
                6, 40, 2, Set.of());
        context.assertEquals(0, hits, "Wave crossed a solid wall");
        context.assertEquals(100f, enemy.getHealth(), "Enemy behind a wall took damage");
        context.assertTrue(context.getBlockState(new BlockPos(3, 2, 3)).isOf(Blocks.OBSIDIAN), "Wall was changed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_empty")
    public void failedVectorDoesNotSpendEnergy(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context);
        context.assertFalse(RiftController.cast(player), "Vector with no target should fail");
        context.assertEquals(100f, RiftController.power(player).energy(), "Failed cast consumed energy");
        context.assertEquals(0, RiftController.power(player).cooldown(AbilityMode.VECTOR), "Failed cast started cooldown");
        remove(player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_vector")
    public void vectorLaunchesOnceAndKeepsMobFlags(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context);
        HuskEntity enemy = husk(context, 3, 1, 5);
        enemy.setNoGravity(true);
        var result = RiftMod.GAUNTLET.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(result.getResult().isAccepted(), "Vector did not acquire a visible enemy");
        context.assertEquals(80f, RiftController.power(player).energy(), "Vector cost is wrong");
        RiftController.releaseVector(player);
        float healthAfterThrow = enemy.getHealth();
        Vec3d velocity = enemy.getVelocity();
        RiftController.releaseVector(player);
        context.assertEquals(82f, healthAfterThrow, "Launch damage is wrong");
        context.assertEquals(healthAfterThrow, enemy.getHealth(), "Duplicate release caused damage");
        context.assertEquals(velocity, enemy.getVelocity(), "Duplicate release changed velocity");
        context.assertTrue(velocity.z > 2, "Enemy was not launched forward");
        context.assertTrue(enemy.hasNoGravity() && enemy.isAiDisabled(), "Original mob flags were changed");
        remove(player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_mode")
    public void sneakUseSwitchesOnlyOnceWhileHeld(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context);
        player.setSneaking(true);
        for (int i = 0; i < 10; i++) RiftMod.GAUNTLET.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertEquals(AbilityMode.SURGE, RiftController.power(player).mode(), "Held sneak-use cycled repeatedly");
        context.assertEquals(100f, RiftController.power(player).energy(), "Switching mode spent energy");
        context.assertFalse(RiftController.cycleMode(player), "Key packet bypassed active-use protection");
        remove(player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_surge")
    public void surgePaysOnceAndStopsAtCooldown(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context);
        RiftController.power(player).cycleMode();
        context.assertTrue(RiftController.cast(player), "Surge should start");
        context.assertEquals(70f, RiftController.power(player).energy(), "Surge cost is wrong");
        context.assertTrue(player.getVelocity().z > 1.5, "Surge did not apply forward velocity");
        context.assertFalse(RiftController.cast(player), "Second surge bypassed active-cast guard");
        context.assertEquals(70f, RiftController.power(player).energy(), "Rejected surge spent energy");
        remove(player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_singularity", tickLimit = 100)
    public void singularityCollapsesAndLeavesBlocksIntact(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context);
        player.setPitch(30);
        RiftController.power(player).cycleMode();
        RiftController.power(player).cycleMode();
        HuskEntity enemy = husk(context, 3, 1, 5);
        context.assertTrue(RiftController.cast(player), "Singularity should start");
        context.assertEquals(20f, RiftController.power(player).energy(), "Singularity cost is wrong");
        context.waitAndRun(75, () -> {
            context.assertTrue(enemy.getHealth() < 65, "Singularity did not deal its collapse damage");
            context.assertTrue(context.getBlockState(new BlockPos(3, 0, 4)).isOf(Blocks.STONE), "Singularity destroyed blocks");
            context.assertTrue(RiftController.power(player).cooldown(AbilityMode.SINGULARITY) > 0, "Ultimate cooldown ended too early");
            remove(player);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_cleanup", tickLimit = 100)
    public void disconnectCleanupCancelsSingularity(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context);
        player.setPitch(30);
        RiftController.power(player).cycleMode();
        RiftController.power(player).cycleMode();
        HuskEntity enemy = husk(context, 3, 1, 5);
        context.assertTrue(RiftController.cast(player), "Singularity should start");
        remove(player);
        context.waitAndRun(75, () -> {
            context.assertEquals(100f, enemy.getHealth(), "Cancelled singularity still damaged entities");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_save")
    public void powerSurvivesNbtRoundTrip(TestContext context) {
        UUID uuid = UUID.randomUUID();
        RiftSaveData original = new RiftSaveData();
        PowerState state = original.player(uuid);
        state.cycleMode();
        state.trySpend(AbilityMode.SINGULARITY);
        NbtCompound nbt = original.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        PowerState restored = RiftSaveData.read(nbt, context.getWorld().getRegistryManager()).player(uuid);
        context.assertEquals(20f, restored.energy(), "Energy refilled after save/load");
        context.assertEquals(240, restored.cooldown(AbilityMode.SINGULARITY), "Cooldown reset after save/load");
        context.assertEquals(AbilityMode.SURGE, restored.mode(), "Mode was not saved");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_codec")
    public void networkStateRoundTrips(TestContext context) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            PowerPayload original = new PowerPayload(37.5f, 100, 2, 10, 20, 200, 6);
            PowerPayload.CODEC.encode(buffer, original);
            context.assertEquals(original, PowerPayload.CODEC.decode(buffer), "HUD payload lost state");
        } finally {
            buffer.release();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rift_recipe")
    public void survivalRecipeAndUnlockAreRegistered(TestContext context) {
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, List.of(
                new ItemStack(Items.ECHO_SHARD), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.ECHO_SHARD),
                new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.CRYING_OBSIDIAN), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CRYING_OBSIDIAN)));
        var recipe = context.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        context.assertTrue(recipe.isPresent(), "Gauntlet crafting recipe is missing");
        ItemStack output = recipe.orElseThrow().value().craft(input, context.getWorld().getRegistryManager());
        context.assertTrue(output.isOf(RiftMod.GAUNTLET) && output.getCount() == 1, "Recipe result is wrong");
        context.assertTrue(context.getWorld().getServer().getAdvancementLoader()
                .get(Identifier.of("rift", "recipes/combat/rift_gauntlet")) != null, "Recipe-book unlock is missing");
        context.complete();
    }

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayerEntity player(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(context.getAbsolute(new Vec3d(3, 1, 1)), 0, 0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(RiftMod.GAUNTLET));
        return player;
    }

    private static HuskEntity husk(TestContext context, int x, int y, int z) {
        HuskEntity entity = context.spawnMob(EntityType.HUSK, x, y, z);
        entity.setAiDisabled(true);
        entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
        entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0);
        entity.setHealth(100);
        return entity;
    }

    private static void remove(ServerPlayerEntity player) {
        RiftController.cleanup(player);
        player.stopUsingItem();
        player.getServer().getPlayerManager().remove(player);
        player.discard();
    }
}
