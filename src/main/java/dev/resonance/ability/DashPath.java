package dev.resonance.ability;

import java.util.function.DoublePredicate;

public final class DashPath {
    public static final double MAX_DISTANCE = 12.0;
    public static final double STEP = 0.25;
    public static final double MIN_DISTANCE = 0.75;

    private DashPath() {
    }

    public static double findDistance(DoublePredicate isSafe) {
        if (!isSafe.test(0.0)) {
            return 0.0;
        }
        double lastSafe = 0.0;
        for (int sample = 1; sample <= MAX_DISTANCE / STEP; sample++) {
            double distance = sample * STEP;
            if (!isSafe.test(distance)) {
                break;
            }
            lastSafe = distance;
        }
        return lastSafe < MIN_DISTANCE ? 0.0 : lastSafe;
    }
}
