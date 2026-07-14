package com.mymod.flux_turret.block.entity;

/** Player-selectable targeting policies. AUTO delegates to each turret's role. */
public enum TargetingMode {
    AUTO("auto"),
    NEAREST("nearest"),
    HIGHEST_HEALTH("highest_health"),
    FASTEST("fastest"),
    HIGHEST_ARMOR("highest_armor"),
    CLUSTER("cluster"),
    BEACON_WAVE("beacon_wave");

    private final String id;

    TargetingMode(String id) {
        this.id = id;
    }

    public String getTranslationKey() {
        return "targeting.flux_turret." + id;
    }

    public TargetingMode next() {
        TargetingMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TargetingMode fromOrdinal(int value) {
        TargetingMode[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, value))];
    }
}
