package dev.overkill;

import dev.overkill.effect.WorldEffects;
import dev.overkill.logic.DevourerMining;
import dev.overkill.logic.GravityGrab;
import dev.overkill.logic.RingOfAbsolute;
import dev.overkill.network.ModNetworking;
import dev.overkill.registry.ModAttachments;
import dev.overkill.registry.ModComponents;
import dev.overkill.registry.ModEntities;
import dev.overkill.registry.ModGameRules;
import dev.overkill.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OverkillMod implements ModInitializer {
	public static final String MOD_ID = "overkill";
	public static final Logger LOG = LoggerFactory.getLogger("OVERKILL");

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Components must exist before items reference them as defaults.
		ModComponents.init();
		ModAttachments.init();
		ModItems.init();
		ModEntities.init();
		ModGameRules.init();
		ModNetworking.init();

		WorldEffects.init();
		DevourerMining.init();
		GravityGrab.init();
		RingOfAbsolute.init();

		LOG.info("OVERKILL loaded. Balance has left the chat.");
	}
}
