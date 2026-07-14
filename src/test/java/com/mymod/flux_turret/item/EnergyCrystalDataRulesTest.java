package com.mymod.flux_turret.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyCrystalDataRulesTest {
    @Test
    void untaggedLegacyCrystalKeepsItsFormerFullCharge() {
        assertTrue(EnergyCrystalDataRules.isLegacy(0));
        assertEquals(100_000, EnergyCrystalDataRules.resolveStoredEnergy(
                false, null, 0, 100_000));
    }

    @Test
    void explicitVersionDistinguishesANewEmptyCrystal() {
        assertFalse(EnergyCrystalDataRules.isLegacy(EnergyCrystalDataRules.CURRENT_VERSION));
        assertEquals(0, EnergyCrystalDataRules.resolveStoredEnergy(
                true, 0, EnergyCrystalDataRules.CURRENT_VERSION, 100_000));
        assertEquals(0, EnergyCrystalDataRules.resolveStoredEnergy(
                false, null, EnergyCrystalDataRules.CURRENT_VERSION, 100_000));
    }

    @Test
    void oldExplicitEnergyIsPreservedAndClamped() {
        assertEquals(42_000, EnergyCrystalDataRules.resolveStoredEnergy(
                true, 42_000, 0, 100_000));
        assertEquals(100_000, EnergyCrystalDataRules.resolveStoredEnergy(
                true, 150_000, 0, 100_000));
        assertEquals(0, EnergyCrystalDataRules.resolveStoredEnergy(
                true, -1, 0, 100_000));
    }

    @Test
    void malformedExplicitEnergyCannotMintALegacyFullCharge() {
        assertEquals(0, EnergyCrystalDataRules.resolveStoredEnergy(
                true, null, 0, 100_000));
    }
}
