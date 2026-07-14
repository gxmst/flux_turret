package com.mymod.flux_turret.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PsychicBeaconRulesTest {
    @Test
    void dawnCostCannotExceedPhysicalCapacity() {
        assertEquals(10_000, PsychicBeaconBlockEntity.getEffectiveDawnCost(50_000, 10_000));
        assertEquals(15_000, PsychicBeaconBlockEntity.getEffectiveDawnCost(15_000, 60_000));
        assertEquals(0, PsychicBeaconBlockEntity.getEffectiveDawnCost(-1, 60_000));
    }

    @Test
    void normalAndFrozenDaylightClocksRemainValid() {
        assertFalse(PsychicBeaconBlockEntity.isBattleClockDiscontinuous(100, 12_000, 120, 12_020));
        assertFalse(PsychicBeaconBlockEntity.isBattleClockDiscontinuous(100, 12_000, 120, 12_000));
    }

    @Test
    void sleepAndTimeCommandsInvalidateTheBattleClock() {
        assertTrue(PsychicBeaconBlockEntity.isBattleClockDiscontinuous(100, 12_000, 101, 23_000));
        assertTrue(PsychicBeaconBlockEntity.isBattleClockDiscontinuous(100, 12_000, 101, 11_000));
        assertTrue(PsychicBeaconBlockEntity.isBattleClockDiscontinuous(-1, -1, 101, 12_001));
    }

    @Test
    void requiredEnergyIncludesRuntimeDrainAndUnreservedRewardCost() {
        assertEquals(37_000, PsychicBeaconBlockEntity.calculateRequiredEnergyThroughReward(
                11_000, 2, 15_000, false));
        assertEquals(22_000, PsychicBeaconBlockEntity.calculateRequiredEnergyThroughReward(
                11_000, 2, 15_000, true));
    }

    @Test
    void requiredEnergySanitizesInputsAndSaturates() {
        assertEquals(0, PsychicBeaconBlockEntity.calculateRequiredEnergyThroughReward(
                -20, -2, -10, false));
        assertEquals(Integer.MAX_VALUE, PsychicBeaconBlockEntity.calculateRequiredEnergyThroughReward(
                Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, false));
    }

    @Test
    void rewardProtectionExpiresAtTheRecordedTick() {
        assertTrue(PsychicBeaconBlockEntity.isRewardProtectionActive(99, 100));
        assertFalse(PsychicBeaconBlockEntity.isRewardProtectionActive(100, 100));
        assertFalse(PsychicBeaconBlockEntity.isRewardProtectionActive(101, 100));
    }
}
