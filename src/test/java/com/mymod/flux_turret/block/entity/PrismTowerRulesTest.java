package com.mymod.flux_turret.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrismTowerRulesTest {
    @Test
    void supportLinksUseSphericalDistance() {
        assertTrue(PrismTowerBlockEntity.Rules.isWithinSphericalRange(12, 0, 0, 12));
        assertFalse(PrismTowerBlockEntity.Rules.isWithinSphericalRange(12, 0, 12, 12));
    }

    @Test
    void reservationExpiresAtItsUntilTick() {
        assertTrue(PrismTowerBlockEntity.Rules.isReservationActive(129L, 130L));
        assertFalse(PrismTowerBlockEntity.Rules.isReservationActive(130L, 130L));
        assertFalse(PrismTowerBlockEntity.Rules.isReservationActive(131L, 130L));
    }

    @Test
    void potentialScanStopsOnceNormalRangeIsMaxed() {
        int limit = PrismTowerBlockEntity.Rules.getPotentialSupportScanLimit(16.0, false);

        assertEquals(11, limit);
        assertEquals(24.0,
                PrismTowerBlockEntity.Rules.calculateEffectiveScanRange(16.0, limit, false),
                0.0001);
        assertEquals(24.0,
                PrismTowerBlockEntity.Rules.calculateEffectiveScanRange(16.0, limit + 100, false),
                0.0001);
    }

    @Test
    void remoteSupportUsesItsOwnBonusAndRangeCap() {
        int limit = PrismTowerBlockEntity.Rules.getPotentialSupportScanLimit(16.0, true);

        assertEquals(16, limit);
        assertEquals(32.0,
                PrismTowerBlockEntity.Rules.calculateEffectiveScanRange(16.0, limit, true),
                0.0001);
    }

    @Test
    void eitherPrismRoleCanKeepTheTowerOperational() {
        assertEquals(500, PrismTowerBlockEntity.Rules.getOperatingEnergyThreshold(1_000, 500));
        assertEquals(500, PrismTowerBlockEntity.Rules.getOperatingEnergyThreshold(500, 1_000));
    }

}
