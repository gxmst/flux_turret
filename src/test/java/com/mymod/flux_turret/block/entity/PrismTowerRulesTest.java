package com.mymod.flux_turret.block.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

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

    @Test
    void legacyUnownedNetworksRemainUsableButCannotJoinOwnedNetworks() {
        UUID owner = UUID.randomUUID();

        assertTrue(canLink(null, "", TurretAccessMode.TEAM,
                null, "", TurretAccessMode.TEAM));
        assertFalse(canLink(owner, "builders", TurretAccessMode.PUBLIC,
                null, "", TurretAccessMode.PUBLIC));
        assertFalse(canLink(null, "", TurretAccessMode.PUBLIC,
                owner, "builders", TurretAccessMode.PUBLIC));
    }

    @Test
    void sameOwnerAlwaysSharesWithoutOpeningTheNetwork() {
        UUID owner = UUID.randomUUID();

        assertTrue(canLink(owner, "", TurretAccessMode.PRIVATE,
                owner, "", TurretAccessMode.PRIVATE));
    }

    @Test
    void teamAndPublicAccessRequireSymmetricConsent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(canLink(first, "builders", TurretAccessMode.TEAM,
                second, "builders", TurretAccessMode.PUBLIC));
        assertFalse(canLink(first, "builders", TurretAccessMode.PRIVATE,
                second, "builders", TurretAccessMode.TEAM));
        assertFalse(canLink(first, "red", TurretAccessMode.PUBLIC,
                second, "blue", TurretAccessMode.TEAM));
        assertTrue(canLink(first, "red", TurretAccessMode.PUBLIC,
                second, "blue", TurretAccessMode.PUBLIC));
    }

    @Test
    void networkCompatibilityIsSymmetric() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        boolean forward = canLink(first, "builders", TurretAccessMode.TEAM,
                second, "builders", TurretAccessMode.PUBLIC);
        boolean reverse = canLink(second, "builders", TurretAccessMode.PUBLIC,
                first, "builders", TurretAccessMode.TEAM);
        assertEquals(forward, reverse);
    }

    private static boolean canLink(UUID ownerA, String teamA, TurretAccessMode accessA,
                                   UUID ownerB, String teamB, TurretAccessMode accessB) {
        return PrismTowerBlockEntity.Rules.canNetworksLink(
                ownerA, teamA, accessA, ownerB, teamB, accessB);
    }

}
