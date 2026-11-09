package dev.rift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerStateTest {
    @Test
    void spendingStartsIndependentCooldowns() {
        PowerState state = new PowerState();
        assertTrue(state.trySpend(AbilityMode.VECTOR));
        assertEquals(80, state.energy());
        assertEquals(50, state.cooldown(AbilityMode.VECTOR));
        assertTrue(state.trySpend(AbilityMode.SURGE));
        assertEquals(50, state.energy());
        assertEquals(0, state.cooldown(AbilityMode.SINGULARITY));
    }

    @Test
    void cooldownSpamDoesNotSpendEnergy() {
        PowerState state = new PowerState();
        assertTrue(state.trySpend(AbilityMode.VECTOR));
        for (int i = 0; i < 100; i++) assertFalse(state.trySpend(AbilityMode.VECTOR));
        assertEquals(80, state.energy());
    }

    @Test
    void insufficientEnergyDoesNotStartCooldown() {
        PowerState state = new PowerState();
        assertTrue(state.trySpend(AbilityMode.SINGULARITY));
        assertFalse(state.trySpend(AbilityMode.SURGE));
        assertEquals(20, state.energy());
        assertEquals(0, state.cooldown(AbilityMode.SURGE));
    }

    @Test
    void regenerationWaitsThirtyTicksAndDoesNotRunDuringChannel() {
        PowerState state = new PowerState();
        state.trySpend(AbilityMode.VECTOR);
        for (int i = 0; i < PowerState.RECHARGE_DELAY; i++) state.tick(false, .5f);
        assertEquals(80, state.energy());
        for (int i = 0; i < 60; i++) state.tick(true, .5f);
        assertEquals(80, state.energy());
        state.tick(false, .5f);
        assertEquals(80.5f, state.energy());
    }

    @Test
    void energyCapsAndCooldownsStopAtZero() {
        PowerState state = new PowerState();
        state.trySpend(AbilityMode.SINGULARITY);
        for (int i = 0; i < 500; i++) state.tick(false, 3);
        assertEquals(PowerState.MAX_ENERGY, state.energy());
        for (AbilityMode mode : AbilityMode.values()) assertEquals(0, state.cooldown(mode));
    }

    @Test
    void corruptSaveValuesAreClamped() {
        PowerState state = new PowerState();
        state.restore(Float.NaN, -7, new int[]{-8, Integer.MAX_VALUE, 9999}, Integer.MAX_VALUE);
        assertEquals(100, state.energy());
        assertEquals(AbilityMode.VECTOR, state.mode());
        assertEquals(0, state.cooldown(AbilityMode.VECTOR));
        assertEquals(40, state.cooldown(AbilityMode.SURGE));
        assertEquals(240, state.cooldown(AbilityMode.SINGULARITY));
        assertEquals(30, state.rechargeDelay());
        state.restore(-10, 2, new int[]{1}, -1);
        assertEquals(0, state.energy());
        assertEquals(AbilityMode.SINGULARITY, state.mode());
        assertEquals(0, state.cooldown(AbilityMode.SINGULARITY));
    }

    @Test
    void persistedStateRoundTripsWithoutResettingCooldowns() {
        PowerState original = new PowerState();
        original.cycleMode();
        original.trySpend(AbilityMode.SINGULARITY);
        original.tick(false, .5f);
        PowerState restored = new PowerState();
        restored.restore(original.energy(), original.mode().ordinal(),
                java.util.Arrays.stream(AbilityMode.values()).mapToInt(original::cooldown).toArray(),
                original.rechargeDelay());
        assertEquals(original.energy(), restored.energy());
        assertEquals(original.mode(), restored.mode());
        assertEquals(original.cooldown(AbilityMode.SINGULARITY), restored.cooldown(AbilityMode.SINGULARITY));
        assertFalse(restored.trySpend(AbilityMode.SINGULARITY));
    }

    @Test
    void modeCyclesExactlyThreeSteps() {
        PowerState state = new PowerState();
        state.cycleMode();
        assertEquals(AbilityMode.SURGE, state.mode());
        state.cycleMode();
        assertEquals(AbilityMode.SINGULARITY, state.mode());
        state.cycleMode();
        assertEquals(AbilityMode.VECTOR, state.mode());
    }

    @Test
    void invalidRegenerationCannotPoisonState() {
        PowerState state = new PowerState();
        state.trySpend(AbilityMode.VECTOR);
        for (int i = 0; i < 100; i++) state.tick(false, Float.NaN);
        state.tick(false, -100);
        state.tick(false, Float.POSITIVE_INFINITY);
        assertEquals(80, state.energy());
    }
}
