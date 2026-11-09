package dev.overkill.effect;

import dev.overkill.OverkillMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WorldEffects {
	private static final Map<ResourceKey<Level>, List<WorldEffect>> ACTIVE = new HashMap<>();

	private WorldEffects() {
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(WorldEffects::tick);
		ServerWorldEvents.UNLOAD.register((server, level) -> endAll(level));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				endAll(level);
			}
			ACTIVE.clear();
		});
		TimeStopEffect.registerHooks();
	}

	public static void add(ServerLevel level, WorldEffect effect) {
		ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(effect);
	}

	public static <T extends WorldEffect> List<T> active(ServerLevel level, Class<T> type) {
		List<WorldEffect> effects = ACTIVE.get(level.dimension());
		if (effects == null || effects.isEmpty()) {
			return List.of();
		}
		List<T> result = new ArrayList<>();
		for (WorldEffect effect : effects) {
			if (type.isInstance(effect)) {
				result.add(type.cast(effect));
			}
		}
		return result;
	}

	private static void tick(ServerLevel level) {
		List<WorldEffect> effects = ACTIVE.get(level.dimension());
		if (effects == null || effects.isEmpty()) {
			return;
		}
		// Snapshot: effects may spawn other effects while ticking.
		for (WorldEffect effect : List.copyOf(effects)) {
			boolean keep;
			try {
				keep = effect.tick(level);
			} catch (RuntimeException e) {
				OverkillMod.LOG.error("World effect {} crashed and was stopped", effect.getClass().getSimpleName(), e);
				keep = false;
			}
			if (!keep) {
				effects.remove(effect);
				safeEnd(effect, level, false);
			}
		}
	}

	private static void endAll(ServerLevel level) {
		List<WorldEffect> effects = ACTIVE.remove(level.dimension());
		if (effects != null) {
			for (WorldEffect effect : effects) {
				safeEnd(effect, level, true);
			}
		}
	}

	private static void safeEnd(WorldEffect effect, ServerLevel level, boolean interrupted) {
		try {
			effect.end(level, interrupted);
		} catch (RuntimeException e) {
			OverkillMod.LOG.error("World effect {} failed to clean up", effect.getClass().getSimpleName(), e);
		}
	}
}
