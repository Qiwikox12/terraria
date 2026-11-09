package dev.resonance.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeTest {
    @Test
    void cyclesForwardAndBackwardFromEveryMode() {
        assertEquals(Mode.PULSE, Mode.WELL.next(1));
        assertEquals(Mode.STEP, Mode.PULSE.next(1));
        assertEquals(Mode.WELL, Mode.STEP.next(1));

        assertEquals(Mode.STEP, Mode.WELL.next(-1));
        assertEquals(Mode.WELL, Mode.PULSE.next(-1));
        assertEquals(Mode.PULSE, Mode.STEP.next(-1));
    }

    @Test
    void treatsZeroDirectionAsNoChange() {
        for (Mode mode : Mode.values()) {
            assertEquals(mode, mode.next(0));
        }
    }

    @Test
    void invalidIndexesFallBackToWell() {
        assertEquals(Mode.WELL, Mode.fromIndex(-1));
        assertEquals(Mode.WELL, Mode.fromIndex(Mode.values().length));
        assertEquals(Mode.WELL, Mode.fromIndex(Integer.MAX_VALUE));
    }
}
