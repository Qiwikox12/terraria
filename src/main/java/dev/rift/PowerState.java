package dev.rift;

public final class PowerState {
    public static final float MAX_ENERGY = 100;
    public static final int RECHARGE_DELAY = 30;

    private float energy = MAX_ENERGY;
    private AbilityMode mode = AbilityMode.VECTOR;
    private final int[] cooldowns = new int[AbilityMode.values().length];
    private int rechargeDelay;

    public float energy() { return energy; }
    public AbilityMode mode() { return mode; }
    public int cooldown(AbilityMode ability) { return cooldowns[ability.ordinal()]; }
    public int rechargeDelay() { return rechargeDelay; }
    public void cycleMode() { mode = mode.next(); }

    public boolean trySpend(AbilityMode ability) {
        if (cooldown(ability) > 0 || energy < ability.cost()) return false;
        energy -= ability.cost();
        cooldowns[ability.ordinal()] = ability.cooldown();
        rechargeDelay = RECHARGE_DELAY;
        return true;
    }

    public boolean tick(boolean channeling, float regenerationPerTick) {
        boolean changed = false;
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i]--;
                changed = true;
            }
        }
        if (rechargeDelay > 0) {
            rechargeDelay--;
            changed = true;
        } else if (!channeling && energy < MAX_ENERGY && Float.isFinite(regenerationPerTick)
                && regenerationPerTick > 0) {
            energy = Math.min(MAX_ENERGY, energy + regenerationPerTick);
            changed = true;
        }
        return changed;
    }

    public void restore(float savedEnergy, int savedMode, int[] savedCooldowns, int savedDelay) {
        energy = Float.isFinite(savedEnergy) ? Math.clamp(savedEnergy, 0, MAX_ENERGY) : MAX_ENERGY;
        mode = AbilityMode.byId(savedMode);
        for (AbilityMode ability : AbilityMode.values()) {
            int id = ability.ordinal();
            cooldowns[id] = id < savedCooldowns.length
                    ? Math.clamp(savedCooldowns[id], 0, ability.cooldown()) : 0;
        }
        rechargeDelay = Math.clamp(savedDelay, 0, RECHARGE_DELAY);
    }

    public void recharge() {
        energy = MAX_ENERGY;
        java.util.Arrays.fill(cooldowns, 0);
        rechargeDelay = 0;
    }
}
