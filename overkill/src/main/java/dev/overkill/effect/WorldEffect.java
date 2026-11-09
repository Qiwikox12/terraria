package dev.overkill.effect;

import net.minecraft.server.level.ServerLevel;

/** A transient, server-side, per-level effect ticked at the end of every world tick. */
public interface WorldEffect {
	/** @return {@code false} once the effect has finished. */
	boolean tick(ServerLevel level);

	/**
	 * Called exactly once when the effect finishes, is cancelled by an error, or its level shuts down.
	 * Must leave the world in a consistent state (e.g. unfreeze entities).
	 */
	default void end(ServerLevel level, boolean interrupted) {
	}
}
