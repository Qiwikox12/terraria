package dev.overkill.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class ModGameRules {
	/** Whether orbital strikes and singularities may destroy terrain. */
	public static final GameRules.Key<GameRules.BooleanValue> GRIEFING =
			GameRuleRegistry.register("overkillGriefing", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	private ModGameRules() {
	}

	public static boolean griefing(Level level) {
		return level.getGameRules().getBoolean(GRIEFING);
	}

	public static void init() {
	}
}
