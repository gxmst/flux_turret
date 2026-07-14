package com.mymod.flux_turret.item;

import org.jetbrains.annotations.Nullable;

/** Pure, registry-independent rules for versioned crystal item energy data. */
public final class EnergyCrystalDataRules {
    public static final int CURRENT_VERSION = 1;

    private EnergyCrystalDataRules() {
    }

    public static boolean isLegacy(int dataVersion) {
        return dataVersion < CURRENT_VERSION;
    }

    public static int resolveStoredEnergy(boolean hasEnergyField, @Nullable Integer explicitEnergy,
            int dataVersion, int maxEnergy) {
        int clampedMax = Math.max(0, maxEnergy);
        if (hasEnergyField) {
            return explicitEnergy == null ? 0 : Math.max(0, Math.min(explicitEnergy, clampedMax));
        }
        return isLegacy(dataVersion) ? clampedMax : 0;
    }
}
