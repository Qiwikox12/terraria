package dev.eventhorizon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorStateTest {
    @Test
    void startsFullInPullModeAndCyclesModes() {
        ReactorState state = new ReactorState();

        assertEquals(ReactorState.MAX_ENERGY, state.energy());
        assertEquals(Ability.PULL, state.mode());
        assertEquals(0, state.resonance(0));

        state.cycleMode();
        assertEquals(Ability.PULSE, state.mode());
        state.cycleMode();
        assertEquals(Ability.BLINK, state.mode());
        state.cycleMode();
        assertEquals(Ability.SINGULARITY, state.mode());
        state.cycleMode();
        assertEquals(Ability.PULL, state.mode());
    }

    @Test
    void cooldownsAreIndependentButShareAGlobalLockout() {
        ReactorState state = new ReactorState();

        assertEquals(1.0, state.commit(Ability.PULL, 0), 1.0e-9);
        assertEquals(40, state.cooldownRemaining(Ability.PULL, 0));
        assertEquals(0, state.cooldownRemaining(Ability.PULSE, 0));
        assertEquals(8, state.globalCooldownRemaining(0));
        assertEquals(ReactorState.Failure.COOLDOWN, state.check(Ability.PULSE, 1));
        assertEquals(0, state.cooldownRemaining(Ability.PULSE, 1));
        assertEquals(ReactorState.Failure.NONE, state.check(Ability.PULSE, 8));

        assertEquals(1.15, state.commit(Ability.PULSE, 8), 1.0e-9);
        assertEquals(60, state.cooldownRemaining(Ability.PULSE, 8));
        assertEquals(32, state.cooldownRemaining(Ability.PULL, 8));
        assertEquals(0, state.cooldownRemaining(Ability.BLINK, 8));
        assertEquals(ReactorState.Failure.COOLDOWN, state.check(Ability.BLINK, 9));
        assertEquals(7, state.globalCooldownRemaining(9));
        assertEquals(0, state.globalCooldownRemaining(16));
        assertEquals(ReactorState.Failure.NONE, state.check(Ability.BLINK, 16));
    }

    @Test
    void boundsDisplayedCooldownsToTheirConfiguredDurations() {
        ReactorState state = ReactorState.restore(new ReactorState.Snapshot(
                100,
                Ability.PULL.ordinal(),
                0,
                -1,
                0,
                Long.MAX_VALUE,
                new long[]{Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE}));

        assertEquals(40, state.cooldownRemaining(Ability.PULL, 0));
        assertEquals(60, state.cooldownRemaining(Ability.PULSE, 0));
        assertEquals(30, state.cooldownRemaining(Ability.BLINK, 0));
        assertEquals(400, state.cooldownRemaining(Ability.SINGULARITY, 0));
        assertEquals(8, state.globalCooldownRemaining(0));
        assertEquals(0, state.cooldownRemaining(Ability.SINGULARITY, Long.MAX_VALUE));
        assertEquals(0, state.globalCooldownRemaining(Long.MAX_VALUE));
    }

    @Test
    void differentOrdinaryAbilitiesBuildResonanceAndUltimateConsumesIt() {
        ReactorState state = new ReactorState();

        assertEquals(1.0, state.commit(Ability.PULL, 0), 1.0e-9);
        assertEquals(1, state.resonance(0));
        assertEquals(1.15, state.commit(Ability.PULSE, 8), 1.0e-9);
        assertEquals(2, state.resonance(8));
        assertEquals(1.30, state.commit(Ability.BLINK, 16), 1.0e-9);
        assertEquals(3, state.resonance(16));

        assertEquals(ReactorState.Failure.NONE, state.check(Ability.SINGULARITY, 24));
        assertEquals(1.45, state.commit(Ability.SINGULARITY, 24), 1.0e-9);
        assertEquals(5, state.energy());
        assertEquals(0, state.resonance(24));
        assertEquals(ReactorState.Failure.ENERGY, state.check(Ability.PULL, 40));
    }

    @Test
    void repeatingTheLastAbilityResetsComboWhileDifferentAbilitiesExtendIt() {
        ReactorState state = new ReactorState();

        state.commit(Ability.PULL, 0);
        state.commit(Ability.PULSE, 8);
        assertEquals(2, state.resonance(8));

        assertEquals(1.30, state.commit(Ability.PULSE, 68), 1.0e-9);
        assertEquals(1, state.resonance(68));
        assertEquals(1.15, state.commit(Ability.BLINK, 76), 1.0e-9);
        assertEquals(2, state.resonance(76));
    }

    @Test
    void comboExpiresAtTheWindowBoundaryAndCanStartAgain() {
        ReactorState state = new ReactorState();

        state.commit(Ability.PULL, 0);
        assertEquals(1, state.resonance(ReactorState.COMBO_WINDOW - 1));
        assertEquals(0, state.resonance(ReactorState.COMBO_WINDOW));
        assertTrue(state.tick(ReactorState.COMBO_WINDOW, false, 4));
        assertEquals(0, state.resonance(ReactorState.COMBO_WINDOW));
        assertFalse(state.tick(ReactorState.COMBO_WINDOW + 1L, false, 4));

        assertEquals(1.0, state.commit(Ability.PULSE, ReactorState.COMBO_WINDOW), 1.0e-9);
        assertEquals(1, state.resonance(ReactorState.COMBO_WINDOW));
    }

    @Test
    void rechargeRequiresContinuousHeldTicksAndDoesNotUseElapsedWorldTime() {
        ReactorState state = stateWithEnergy(84);

        assertFalse(state.tick(0, true, 4));
        assertFalse(state.tick(1, true, 4));
        assertFalse(state.tick(10_000, false, 4));
        assertEquals(84, state.energy());

        assertFalse(state.tick(10_001, true, 4));
        assertFalse(state.tick(10_002, true, 4));
        assertFalse(state.tick(10_003, true, 4));
        assertTrue(state.tick(10_004, true, 4));
        assertEquals(85, state.energy());

        assertFalse(state.tick(20_000, true, 4));
        assertEquals(85, state.energy());
    }

    @Test
    void rechargeFractionIsNotSavedAndInvalidIntervalsStillMakeProgress() {
        ReactorState state = stateWithEnergy(84);
        assertFalse(state.tick(0, true, 4));
        assertFalse(state.tick(1, true, 4));
        assertFalse(state.tick(2, true, 4));

        ReactorState restored = ReactorState.restore(state.snapshot());
        assertFalse(restored.tick(3, true, 4));
        assertEquals(84, restored.energy());

        assertTrue(restored.tick(4, true, 0));
        assertEquals(85, restored.energy());
    }

    @Test
    void rejectedCommitsDoNotMutateState() {
        ReactorState noResonance = new ReactorState();
        ReactorState.Snapshot noResonanceBefore = noResonance.snapshot();
        assertEquals(ReactorState.Failure.RESONANCE, noResonance.check(Ability.SINGULARITY, 0));
        assertThrows(IllegalStateException.class, () -> noResonance.commit(Ability.SINGULARITY, 0));
        assertStateEquals(noResonanceBefore, noResonance);

        ReactorState noEnergy = stateWithEnergy(0);
        ReactorState.Snapshot noEnergyBefore = noEnergy.snapshot();
        assertEquals(ReactorState.Failure.ENERGY, noEnergy.check(Ability.PULL, 0));
        assertThrows(IllegalStateException.class, () -> noEnergy.commit(Ability.PULL, 0));
        assertStateEquals(noEnergyBefore, noEnergy);

        ReactorState cooldown = new ReactorState();
        cooldown.commit(Ability.PULL, 0);
        ReactorState.Snapshot cooldownBefore = cooldown.snapshot();
        assertEquals(ReactorState.Failure.COOLDOWN, cooldown.check(Ability.BLINK, 1));
        assertThrows(IllegalStateException.class, () -> cooldown.commit(Ability.BLINK, 1));
        assertEquals(ReactorState.Failure.COOLDOWN, cooldown.check(Ability.PULL, 8));
        assertThrows(IllegalStateException.class, () -> cooldown.commit(Ability.PULL, 8));
        assertStateEquals(cooldownBefore, cooldown);
    }

    @Test
    void energyRefillsAreClampedAndNegativeAmountsAreIgnored() {
        ReactorState state = stateWithEnergy(95);

        assertEquals(5, state.addEnergy(10));
        assertEquals(100, state.energy());
        assertEquals(0, state.addEnergy(1));
        assertEquals(0, state.addEnergy(-10));
        assertEquals(100, state.energy());

        ReactorState empty = stateWithEnergy(0);
        assertEquals(100, empty.addEnergy(Integer.MAX_VALUE));
        assertEquals(100, empty.energy());
    }

    @Test
    void snapshotRoundTripsGameplayStateAndProtectsItsCooldownArray() {
        ReactorState state = new ReactorState();
        state.cycleMode();
        state.commit(Ability.PULL, 10);
        state.commit(Ability.PULSE, 18);

        ReactorState restored = ReactorState.restore(state.snapshot());
        assertEquals(62, restored.energy());
        assertEquals(Ability.PULSE, restored.mode());
        assertEquals(2, restored.resonance(18));
        assertEquals(2, restored.resonance(156));
        assertEquals(32, restored.cooldownRemaining(Ability.PULL, 18));
        assertEquals(60, restored.cooldownRemaining(Ability.PULSE, 18));
        assertEquals(8, restored.globalCooldownRemaining(18));

        long[] timers = {40, 60, 30, 400};
        ReactorState.Snapshot snapshot = new ReactorState.Snapshot(
                75, Ability.BLINK.ordinal(), 2, Ability.PULSE.ordinal(), 200, 108, timers);
        timers[0] = 0;
        assertEquals(40, snapshot.readyAt()[0]);

        long[] exposedTimers = snapshot.readyAt();
        assertNotSame(snapshot.readyAt(), exposedTimers);
        exposedTimers[1] = 0;
        ReactorState fromDefensiveSnapshot = ReactorState.restore(snapshot);
        assertEquals(60, fromDefensiveSnapshot.cooldownRemaining(Ability.PULSE, 0));
        assertEquals(2, fromDefensiveSnapshot.resonance(100));
    }

    @Test
    void restoreClampsMalformedValuesAndHandlesInvalidOrShortTimerArrays() {
        ReactorState invalid = ReactorState.restore(new ReactorState.Snapshot(
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Long.MIN_VALUE,
                Long.MIN_VALUE,
                new long[]{Long.MIN_VALUE, Long.MAX_VALUE, -1}));

        assertEquals(0, invalid.energy());
        assertEquals(Ability.PULL, invalid.mode());
        assertEquals(0, invalid.resonance(0));
        assertEquals(0, invalid.globalCooldownRemaining(0));
        assertEquals(0, invalid.cooldownRemaining(Ability.PULL, 0));
        assertEquals(60, invalid.cooldownRemaining(Ability.PULSE, 0));
        assertEquals(0, invalid.cooldownRemaining(Ability.BLINK, 0));
        assertEquals(0, invalid.cooldownRemaining(Ability.SINGULARITY, 0));

        ReactorState clamped = ReactorState.restore(new ReactorState.Snapshot(
                Integer.MAX_VALUE,
                Ability.BLINK.ordinal(),
                Integer.MAX_VALUE,
                Ability.PULSE.ordinal(),
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                new long[]{0, 0, 0, 0, Long.MAX_VALUE}));
        assertEquals(100, clamped.energy());
        assertEquals(Ability.BLINK, clamped.mode());
        assertEquals(3, clamped.resonance(0));
        assertEquals(0, clamped.cooldownRemaining(Ability.SINGULARITY, 0));
        assertEquals(8, clamped.globalCooldownRemaining(0));

        ReactorState nullTimers = ReactorState.restore(new ReactorState.Snapshot(
                50, 0, 0, -1, 0, 0, null));
        assertArrayEquals(new long[Ability.values().length], nullTimers.snapshot().readyAt());
        assertEquals(0, new ReactorState.Snapshot(50, 0, 0, -1, 0, 0, null).readyAt().length);
        assertEquals(0, nullTimers.cooldownRemaining(Ability.PULL, 0));
        assertEquals(ReactorState.MAX_ENERGY, ReactorState.restore(null).energy());
    }

    @Test
    void timeMathSaturatesAtTheLongBoundary() {
        ReactorState state = new ReactorState();
        long castAt = Long.MAX_VALUE - 5;

        state.commit(Ability.PULL, castAt);
        assertEquals(5, state.cooldownRemaining(Ability.PULL, castAt));
        assertEquals(5, state.globalCooldownRemaining(castAt));
        assertEquals(1, state.resonance(Long.MAX_VALUE - 1));
        assertEquals(0, state.resonance(Long.MAX_VALUE));
        assertEquals(0, state.cooldownRemaining(Ability.PULL, Long.MAX_VALUE));
        assertTrue(state.tick(Long.MAX_VALUE, false, 4));
    }

    private static ReactorState stateWithEnergy(int energy) {
        return ReactorState.restore(new ReactorState.Snapshot(
                energy, Ability.PULL.ordinal(), 0, -1, 0, 0, new long[Ability.values().length]));
    }

    private static void assertStateEquals(ReactorState.Snapshot expected, ReactorState actual) {
        ReactorState.Snapshot actualSnapshot = actual.snapshot();
        assertEquals(expected.energy(), actualSnapshot.energy());
        assertEquals(expected.mode(), actualSnapshot.mode());
        assertEquals(expected.resonance(), actualSnapshot.resonance());
        assertEquals(expected.lastAbility(), actualSnapshot.lastAbility());
        assertEquals(expected.comboExpiresAt(), actualSnapshot.comboExpiresAt());
        assertEquals(expected.globalReadyAt(), actualSnapshot.globalReadyAt());
        assertArrayEquals(expected.readyAt(), actualSnapshot.readyAt());
    }
}
