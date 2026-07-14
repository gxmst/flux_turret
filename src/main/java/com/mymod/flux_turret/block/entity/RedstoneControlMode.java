package com.mymod.flux_turret.block.entity;

/** Explicit redstone behavior; the legacy behavior is DISABLE_WHEN_POWERED. */
public enum RedstoneControlMode {
    DISABLE_WHEN_POWERED("disable_when_powered"),
    REQUIRE_SIGNAL("require_signal"),
    IGNORE("ignore");

    private final String id;

    RedstoneControlMode(String id) {
        this.id = id;
    }

    public boolean blocks(boolean hasSignal) {
        return switch (this) {
            case DISABLE_WHEN_POWERED -> hasSignal;
            case REQUIRE_SIGNAL -> !hasSignal;
            case IGNORE -> false;
        };
    }

    public String getTranslationKey() {
        return "redstone.flux_turret." + id;
    }

    public RedstoneControlMode next() {
        RedstoneControlMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static RedstoneControlMode fromOrdinal(int value) {
        RedstoneControlMode[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, value))];
    }
}
