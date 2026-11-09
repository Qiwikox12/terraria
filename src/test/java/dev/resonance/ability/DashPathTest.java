package dev.resonance.ability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashPathTest {
    @Test
    void stopsAtTheFirstBlockedSampleWithoutSkippingTheObstruction() {
        List<Double> probes = new ArrayList<>();
        double distance = DashPath.findDistance(sample -> {
            probes.add(sample);
            return sample < 2.0 || sample >= 3.0;
        });

        assertEquals(1.75, distance, 0.0);
        assertEquals(9, probes.size());
        assertEquals(2.0, probes.getLast(), 0.0);
        assertFalse(probes.contains(3.0));
    }

    @Test
    void rejectsPathsShorterThanMinimumButAcceptsTheBoundary() {
        assertEquals(0.0, DashPath.findDistance(sample -> sample <= 0.5), 0.0);
        assertEquals(DashPath.MIN_DISTANCE, DashPath.findDistance(sample -> sample <= 0.75), 0.0);
    }

    @Test
    void returnsZeroWhenTheStartingPositionIsUnsafe() {
        List<Double> probes = new ArrayList<>();
        double distance = DashPath.findDistance(sample -> {
            probes.add(sample);
            return false;
        });

        assertEquals(0.0, distance, 0.0);
        assertEquals(List.of(0.0), probes);
    }

    @Test
    void capsAnUnobstructedPathAtMaximumDistance() {
        List<Double> probes = new ArrayList<>();
        double distance = DashPath.findDistance(sample -> {
            probes.add(sample);
            return true;
        });

        assertEquals(DashPath.MAX_DISTANCE, distance, 0.0);
        assertEquals(49, probes.size());
        assertTrue(probes.contains(DashPath.MAX_DISTANCE));
    }
}
