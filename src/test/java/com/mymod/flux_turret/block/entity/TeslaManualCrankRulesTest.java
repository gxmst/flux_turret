package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.util.TeslaCrankRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeslaManualCrankRulesTest {
    private static final int COOLDOWN = 8;

    @Test
    void firstCrankAndElapsedCooldownAreAllowed() {
        assertTrue(TeslaCrankRules.isCooldownReady(100L, Long.MIN_VALUE, COOLDOWN));
        assertTrue(TeslaCrankRules.isCooldownReady(100L + COOLDOWN, 100L, COOLDOWN));
    }

    @Test
    void repeatedPacketsInsideCooldownAreRejected() {
        assertFalse(TeslaCrankRules.isCooldownReady(100L, 100L, COOLDOWN));
        assertFalse(TeslaCrankRules.isCooldownReady(100L + COOLDOWN - 1L, 100L, COOLDOWN));
    }

    @Test
    void aGameTimeRollbackDoesNotLockCrankingForever() {
        assertTrue(TeslaCrankRules.isCooldownReady(5L, 10_000L, COOLDOWN));
    }
}
