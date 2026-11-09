package dev.resonance;

import dev.resonance.ability.ResonanceAbilities;
import dev.resonance.item.FieldGuideItem;
import dev.resonance.item.GauntletItem;
import dev.resonance.network.CycleModePayload;
import dev.resonance.network.OpenGuidePayload;
import dev.resonance.network.StatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class Resonance implements ModInitializer {
    public static final String ID = "resonance";
    public static final Item CORE = Registry.register(Registries.ITEM, id("resonance_core"),
            new Item(new Item.Settings().rarity(Rarity.RARE)));
    public static final GauntletItem GAUNTLET = Registry.register(Registries.ITEM, id("gauntlet"),
            new GauntletItem(new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item FIELD_GUIDE = Registry.register(Registries.ITEM, id("field_guide"),
            new FieldGuideItem(new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final SoundEvent WELL_SOUND = registerSound("well");
    public static final SoundEvent PULSE_SOUND = registerSound("pulse");
    public static final SoundEvent STEP_SOUND = registerSound("step");
    public static final SoundEvent SWITCH_SOUND = registerSound("switch");

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    private static SoundEvent registerSound(String path) {
        return Registry.register(Registries.SOUND_EVENT, id(path), SoundEvent.of(id(path)));
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM_GROUP, id("resonance"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.resonance"))
                .icon(() -> new ItemStack(GAUNTLET))
                .entries((context, entries) -> {
                    entries.add(CORE);
                    entries.add(GAUNTLET);
                    entries.add(FIELD_GUIDE);
                }).build());

        PayloadTypeRegistry.playC2S().register(CycleModePayload.ID, CycleModePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenGuidePayload.ID, OpenGuidePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CycleModePayload.ID,
                (payload, context) -> ResonanceAbilities.cycleMode(context.player(), payload.direction()));
        ResonanceAbilities.register();
    }
}
