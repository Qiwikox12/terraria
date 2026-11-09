package dev.resonance.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityRulesTest {
    @Test
    void clampsEnergyToItsValidRange() {
        assertEquals(0, AbilityRules.clampEnergy(-20));
        assertEquals(0, AbilityRules.clampEnergy(0));
        assertEquals(73, AbilityRules.clampEnergy(73));
        assertEquals(AbilityRules.MAX_ENERGY, AbilityRules.clampEnergy(AbilityRules.MAX_ENERGY));
        assertEquals(AbilityRules.MAX_ENERGY, AbilityRules.clampEnergy(AbilityRules.MAX_ENERGY + 20));
    }

    @Test
    void rechargeAddsSixAndSaturatesAtMaximum() {
        assertEquals(6, AbilityRules.recharge(-20));
        assertEquals(AbilityRules.MAX_ENERGY, AbilityRules.recharge(AbilityRules.MAX_ENERGY - 1));
        assertEquals(AbilityRules.MAX_ENERGY, AbilityRules.recharge(AbilityRules.MAX_ENERGY));
        assertEquals(AbilityRules.MAX_ENERGY, AbilityRules.recharge(AbilityRules.MAX_ENERGY + 20));
    }

    @Test
    void pulseDamageAppliesDistanceFalloffAndDetonationBonus() {
        assertEquals(12.0f, AbilityRules.pulseDamage(false, 0), 0.0f);
        assertEquals(24.0f, AbilityRules.pulseDamage(true, 0), 0.0f);
        assertEquals(9.6f, AbilityRules.pulseDamage(false, AbilityRules.PULSE_RADIUS / 2), 1.0e-6f);
        assertEquals(19.2f, AbilityRules.pulseDamage(true, AbilityRules.PULSE_RADIUS / 2), 1.0e-6f);
        assertEquals(7.2f, AbilityRules.pulseDamage(false, AbilityRules.PULSE_RADIUS), 1.0e-6f);
        assertEquals(14.4f, AbilityRules.pulseDamage(true, AbilityRules.PULSE_RADIUS), 1.0e-6f);
        assertEquals(7.2f, AbilityRules.pulseDamage(false, AbilityRules.PULSE_RADIUS * 2), 1.0e-6f);
        assertEquals(12.0f, AbilityRules.pulseDamage(false, -1), 0.0f);
    }
}
