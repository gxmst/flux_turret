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
}
