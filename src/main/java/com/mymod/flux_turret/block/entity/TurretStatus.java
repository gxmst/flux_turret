package com.mymod.flux_turret.block.entity;

/** Compact server-authoritative reason shown by the built-in diagnostics panel. */
public enum TurretStatus {
    FIRING("firing"),
    TRACKING("tracking"),
    WARMING_UP("warming_up"),
    COOLDOWN("cooldown"),
    NO_TARGET("no_target"),
    NO_ENERGY("no_energy"),
    REDSTONE_STOP("redstone_stop"),
    STRUCTURE_INVALID("structure_invalid");

    private final String id;

    TurretStatus(String id) {
        this.id = id;
    }

    public String getTranslationKey() {
        return "status.flux_turret." + id;
    }

    public static TurretStatus fromOrdinal(int value) {
        TurretStatus[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, value))];
    }
}
