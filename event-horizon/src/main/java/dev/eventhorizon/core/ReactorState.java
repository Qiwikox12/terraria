package dev.eventhorizon.core;

import java.util.Objects;

public final class ReactorState {
    public static final int MAX_ENERGY = 100;
    public static final int COMBO_WINDOW = 160;
    public static final int GLOBAL_COOLDOWN = 8;

    private int energy = MAX_ENERGY;
    private Ability mode = Ability.PULL;
    private int resonance;
    private Ability lastAbility;
    private long comboExpiresAt;
    private long globalReadyAt;
    private final long[] readyAt = new long[Ability.values().length];
    private int rechargeProgress;

    public ReactorState() {
    }

    public Ability mode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
    }

    public int energy() {
        return energy;
    }

    public int resonance(long now) {
        return resonance > 0 && now < comboExpiresAt ? resonance : 0;
    }

    public int cooldownRemaining(Ability ability, long now) {
        Objects.requireNonNull(ability, "ability");
        return remainingTicks(readyAt[ability.ordinal()], now, ability.cooldownTicks());
    }

    public int globalCooldownRemaining(long now) {
        return remainingTicks(globalReadyAt, now, GLOBAL_COOLDOWN);
    }

    public boolean tick(long now, boolean holding, int rechargeInterval) {
        boolean changed = false;

        if (holding && energy < MAX_ENERGY) {
            rechargeProgress++;
            if (rechargeProgress >= Math.max(1, rechargeInterval)) {
                rechargeProgress = 0;
                energy++;
                changed = true;
            }
        } else {
            rechargeProgress = 0;
        }

        if (resonance > 0 && now >= comboExpiresAt) {
            clearCombo();
            changed = true;
        }

        return changed;
    }

    public Failure check(Ability ability, long now) {
        Objects.requireNonNull(ability, "ability");
        if (globalCooldownRemaining(now) > 0 || cooldownRemaining(ability, now) > 0) {
            return Failure.COOLDOWN;
        }
        if (energy < ability.cost()) {
            return Failure.ENERGY;
        }
        if (ability == Ability.SINGULARITY && resonance(now) < 3) {
            return Failure.RESONANCE;
        }
        return Failure.NONE;
    }

    public double commit(Ability ability, long now) {
        Failure failure = check(ability, now);
        if (failure != Failure.NONE) {
            throw new IllegalStateException("Cannot commit " + ability + ": " + failure);
        }

        int precastResonance = resonance(now);
        energy -= ability.cost();
        readyAt[ability.ordinal()] = addTicks(now, ability.cooldownTicks());
        globalReadyAt = addTicks(now, GLOBAL_COOLDOWN);

        if (ability == Ability.SINGULARITY) {
            clearCombo();
            return 1.45;
        }

        if (precastResonance == 0 || lastAbility == null || lastAbility == ability) {
            resonance = 1;
        } else {
            resonance = Math.min(3, precastResonance + 1);
        }
        lastAbility = ability;
        comboExpiresAt = addTicks(now, COMBO_WINDOW);
        return 1.0 + 0.15 * precastResonance;
    }

    public int addEnergy(int amount) {
        if (amount <= 0 || energy >= MAX_ENERGY) {
            return 0;
        }

        int added = Math.min(amount, MAX_ENERGY - energy);
        energy += added;
        if (energy == MAX_ENERGY) {
            rechargeProgress = 0;
        }
        return added;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                energy,
                mode.ordinal(),
                resonance,
                lastAbility == null ? -1 : lastAbility.ordinal(),
                comboExpiresAt,
                globalReadyAt,
                readyAt);
    }

    public static ReactorState restore(Snapshot snapshot) {
        if (snapshot == null) {
            return new ReactorState();
        }

        ReactorState state = new ReactorState();
        state.energy = clamp(snapshot.energy(), 0, MAX_ENERGY);

        Ability restoredMode = abilityAt(snapshot.mode());
        state.mode = restoredMode == null ? Ability.PULL : restoredMode;

        Ability restoredLastAbility = abilityAt(snapshot.lastAbility());
        int restoredResonance = clamp(snapshot.resonance(), 0, 3);
        if (restoredResonance == 0 || restoredLastAbility == null) {
            state.resonance = 0;
            state.lastAbility = null;
            state.comboExpiresAt = 0;
        } else {
            state.resonance = restoredResonance;
            state.lastAbility = restoredLastAbility;
            state.comboExpiresAt = nonNegative(snapshot.comboExpiresAt());
        }

        state.globalReadyAt = nonNegative(snapshot.globalReadyAt());
        long[] savedReadyAt = snapshot.readyAt();
        int timersToRestore = Math.min(savedReadyAt.length, state.readyAt.length);
        for (int i = 0; i < timersToRestore; i++) {
            state.readyAt[i] = nonNegative(savedReadyAt[i]);
        }
        return state;
    }

    private void clearCombo() {
        resonance = 0;
        lastAbility = null;
        comboExpiresAt = 0;
    }

    private static int remainingTicks(long readyAt, long now, int maximum) {
        if (readyAt <= now) {
            return 0;
        }

        long remaining = readyAt - now;
        if (remaining < 0) {
            return maximum;
        }
        return (int) Math.min(remaining, maximum);
    }

    private static long addTicks(long now, int ticks) {
        if (now > Long.MAX_VALUE - ticks) {
            return Long.MAX_VALUE;
        }
        return now + ticks;
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Ability abilityAt(int ordinal) {
        Ability[] abilities = Ability.values();
        return ordinal >= 0 && ordinal < abilities.length ? abilities[ordinal] : null;
    }

    public enum Failure {
        NONE,
        ENERGY,
        COOLDOWN,
        RESONANCE
    }

    public record Snapshot(
            int energy,
            int mode,
            int resonance,
            int lastAbility,
            long comboExpiresAt,
            long globalReadyAt,
            long[] readyAt) {
        public Snapshot {
            readyAt = readyAt == null ? new long[0] : readyAt.clone();
        }

        @Override
        public long[] readyAt() {
            return readyAt.clone();
        }
    }
}
