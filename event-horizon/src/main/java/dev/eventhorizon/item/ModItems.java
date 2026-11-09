package dev.eventhorizon.item;

import dev.eventhorizon.EventHorizon;
import dev.eventhorizon.network.GuidePayload;
import dev.eventhorizon.server.GravitySystem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public final class ModItems {
    public static final Item GRAVITY_GAUNTLET = register("gravity_gauntlet", new Item(
        new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)) {
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (user instanceof ServerPlayerEntity player) GravitySystem.cast(player);
            return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> lines, TooltipType type) {
            lines.add(Text.translatable("tooltip.eventhorizon.gauntlet").formatted(Formatting.AQUA));
            lines.add(Text.translatable("tooltip.eventhorizon.controls").formatted(Formatting.GRAY));
            lines.add(Text.translatable("tooltip.eventhorizon.safe").formatted(Formatting.DARK_GRAY));
        }
    });
    public static final Item GRAVITY_CORE = register("gravity_core", descriptive("core", Rarity.RARE));
    public static final Item FLUX_CELL = register("flux_cell", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)) {
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (user instanceof ServerPlayerEntity player && GravitySystem.recharge(player)) {
                if (!user.isCreative()) stack.decrement(1);
            }
            return TypedActionResult.success(stack, world.isClient);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> lines, TooltipType type) {
            lines.add(Text.translatable("tooltip.eventhorizon.cell").formatted(Formatting.AQUA));
        }
    });
    public static final Item FIELD_MANUAL = register("field_manual", new Item(new Item.Settings().maxCount(1)) {
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (user instanceof ServerPlayerEntity player) ServerPlayNetworking.send(player, new GuidePayload());
            return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> lines, TooltipType type) {
            lines.add(Text.translatable("tooltip.eventhorizon.manual").formatted(Formatting.GRAY));
        }
    });

    private static Item descriptive(String key, Rarity rarity) {
        return new Item(new Item.Settings().rarity(rarity)) {
            @Override
            public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> lines, TooltipType type) {
                lines.add(Text.translatable("tooltip.eventhorizon." + key).formatted(Formatting.GRAY));
            }
        };
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, EventHorizon.id(name), item);
    }

    public static boolean holdingGauntlet(PlayerEntity player) {
        return player.getMainHandStack().isOf(GRAVITY_GAUNTLET) || player.getOffHandStack().isOf(GRAVITY_GAUNTLET);
    }

    public static void initialize() {
        Registry.register(Registries.ITEM_GROUP, EventHorizon.id("arsenal"), FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.eventhorizon"))
            .icon(() -> new ItemStack(GRAVITY_GAUNTLET))
            .entries((context, entries) -> {
                entries.add(GRAVITY_GAUNTLET);
                entries.add(GRAVITY_CORE);
                entries.add(FLUX_CELL);
                entries.add(FIELD_MANUAL);
            }).build());
    }
}
