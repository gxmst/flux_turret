package com.mymod.flux_turret.item;

import java.util.function.Predicate;

/** Registry-independent validation for saved active-module masks. */
public final class TurretUpgradeLoadout {
    private TurretUpgradeLoadout() {
    }

    public static int normalizeActiveMask(int requestedMask, int installedMask,
            TurretUpgradeType.Slot slot, Predicate<TurretUpgradeType> supported) {
        int eligibleMask = 0;
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if (type.getSlot() == slot && (installedMask & type.getMask()) != 0 && supported.test(type)) {
                eligibleMask |= type.getMask();
            }
        }
        int selectedMask = requestedMask & eligibleMask;
        return Integer.bitCount(selectedMask) == 1
                ? selectedMask : Integer.lowestOneBit(eligibleMask);
    }
}
