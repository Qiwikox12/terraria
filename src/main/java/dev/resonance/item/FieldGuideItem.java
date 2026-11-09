package dev.resonance.item;

import dev.resonance.network.OpenGuidePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class FieldGuideItem extends Item {
    public FieldGuideItem(Settings settings) {
        super(settings.maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity player && ServerPlayNetworking.canSend(player, OpenGuidePayload.ID)) {
            ServerPlayNetworking.send(player, new OpenGuidePayload());
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }
}
