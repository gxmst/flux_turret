package com.mymod.flux_turret.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargeHelperTest {
    @Test
    void energySignalHandlesEmptyAndInvalidCapacity() {
        assertEquals(0, ChargeHelper.energySignal(0, 100));
        assertEquals(0, ChargeHelper.energySignal(50, 0));
        assertEquals(0, ChargeHelper.energySignal(-1, 100));
    }

    @Test
    void energySignalScalesAndClampsToComparatorRange() {
        assertEquals(7, ChargeHelper.energySignal(50, 100));
        assertEquals(15, ChargeHelper.energySignal(100, 100));
        assertEquals(15, ChargeHelper.energySignal(200, 100));
    }

    @Test
    void totalChargeUsesRateWithoutOverflowOrNegativeEnergy() {
        assertEquals(10_000, ChargeHelper.totalCharge(50, 200));
        assertEquals(0, ChargeHelper.totalCharge(-50, 200));
        assertEquals(0, ChargeHelper.totalCharge(50, 0));
        assertEquals(Integer.MAX_VALUE, ChargeHelper.totalCharge(Integer.MAX_VALUE, 2));
    }
}
