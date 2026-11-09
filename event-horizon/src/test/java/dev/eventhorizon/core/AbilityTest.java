package dev.eventhorizon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityTest {
    @Test
    void exposesTheConfiguredAbilityMetadata() {
        assertEquals(16, Ability.PULL.cost());
        assertEquals(40, Ability.PULL.cooldownTicks());
        assertEquals(0x56E7F4, Ability.PULL.rgb());
        assertEquals("ability.eventhorizon.pull", Ability.PULL.translationKey());

        assertEquals(22, Ability.PULSE.cost());
        assertEquals(60, Ability.PULSE.cooldownTicks());
        assertEquals(0xFFB35C, Ability.PULSE.rgb());
        assertEquals("ability.eventhorizon.pulse", Ability.PULSE.translationKey());

        assertEquals(12, Ability.BLINK.cost());
        assertEquals(30, Ability.BLINK.cooldownTicks());
        assertEquals(0xB39DFF, Ability.BLINK.rgb());
        assertEquals("ability.eventhorizon.blink", Ability.BLINK.translationKey());

        assertEquals(45, Ability.SINGULARITY.cost());
        assertEquals(400, Ability.SINGULARITY.cooldownTicks());
        assertEquals(0xF279CF, Ability.SINGULARITY.rgb());
        assertEquals("ability.eventhorizon.singularity", Ability.SINGULARITY.translationKey());
    }

    @Test
    void cyclesInOrderAndWrapsAround() {
        assertEquals(Ability.PULSE, Ability.PULL.next());
        assertEquals(Ability.BLINK, Ability.PULSE.next());
        assertEquals(Ability.SINGULARITY, Ability.BLINK.next());
        assertEquals(Ability.PULL, Ability.SINGULARITY.next());
    }
}
