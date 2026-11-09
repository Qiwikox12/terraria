package dev.lumen.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResonanceMathTest {
    @Test
    void sphereIncludesBoundaryButNotCorners() {
        assertTrue(ResonanceMath.insideSphere(14, 0, 0, 14));
        assertTrue(ResonanceMath.insideSphere(0, -14, 0, 14));
        assertFalse(ResonanceMath.insideSphere(14, 14, 14, 14));
        assertFalse(ResonanceMath.insideSphere(15, 0, 0, 14));
    }

    @Test
    void sphereUsesWideIntegerMath() {
        assertTrue(ResonanceMath.insideSphere(Integer.MAX_VALUE, 0, 0, Integer.MAX_VALUE));
        assertFalse(ResonanceMath.insideSphere(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, Integer.MAX_VALUE));
    }

    @Test
    void signalsRemainStableThenFadeSmoothly() {
        assertEquals(0.0f, ResonanceMath.signalAlpha(-1, 180));
        assertEquals(1.0f, ResonanceMath.signalAlpha(0, 180));
        assertEquals(1.0f, ResonanceMath.signalAlpha(140, 180));
        assertEquals(0.5f, ResonanceMath.signalAlpha(160, 180));
        assertEquals(0.0f, ResonanceMath.signalAlpha(180, 180));
    }

    @Test
    void pulseEasesOutAndStaysBounded() {
        assertEquals(0.0f, ResonanceMath.pulseProgress(0, 40));
        assertEquals(0.75f, ResonanceMath.pulseProgress(20, 40));
        assertEquals(1.0f, ResonanceMath.pulseProgress(40, 40));
        assertEquals(1.0f, ResonanceMath.pulseProgress(80, 40));
    }

    @Test
    void clampsInvalidConfigValues() {
        assertEquals(4, ResonanceMath.clamp(-20, 4, 24));
        assertEquals(24, ResonanceMath.clamp(80, 4, 24));
        assertEquals(14, ResonanceMath.clamp(14, 4, 24));
    }
}
