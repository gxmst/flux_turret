package com.mymod.flux_turret.block.entity;

/** Who may change modes, install/recover modules, or manually operate a turret. */
public enum TurretAccessMode {
    PRIVATE("private"),
    TEAM("team"),
    PUBLIC("public");

    private final String id;

    TurretAccessMode(String id) {
        this.id = id;
    }

    public String getTranslationKey() {
        return "access.flux_turret." + id;
    }

    public TurretAccessMode next() {
        TurretAccessMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TurretAccessMode fromOrdinal(int value) {
        TurretAccessMode[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, value))];
    }
}
