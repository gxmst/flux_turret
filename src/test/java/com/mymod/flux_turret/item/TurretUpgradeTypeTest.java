package com.mymod.flux_turret.item;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretUpgradeTypeTest {
    @Test
    void everyUpgradeHasAUniqueSingleBitMask() {
        Set<Integer> masks = new HashSet<>();
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            int mask = type.getMask();
            assertTrue(mask > 0 && (mask & (mask - 1)) == 0, type.name());
            assertTrue(masks.add(mask), type.name());
        }
        assertEquals(TurretUpgradeType.values().length, masks.size());
    }

    @Test
    void everyTurretFamilyHasOneUtilityAndTwoWeaponChoices() {
        assertEquals(8, java.util.Arrays.stream(TurretUpgradeType.values())
                .filter(type -> type.getSlot() == TurretUpgradeType.Slot.WEAPON).count());
        assertEquals(4, java.util.Arrays.stream(TurretUpgradeType.values())
                .filter(type -> type.getSlot() == TurretUpgradeType.Slot.UTILITY).count());
    }
}
