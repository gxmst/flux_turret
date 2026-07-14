package com.mymod.flux_turret.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretControlModeTest {
    @Test
    void redstoneModesHaveExplicitAndOppositeSemantics() {
        assertTrue(RedstoneControlMode.DISABLE_WHEN_POWERED.blocks(true));
        assertFalse(RedstoneControlMode.DISABLE_WHEN_POWERED.blocks(false));
        assertFalse(RedstoneControlMode.REQUIRE_SIGNAL.blocks(true));
        assertTrue(RedstoneControlMode.REQUIRE_SIGNAL.blocks(false));
        assertFalse(RedstoneControlMode.IGNORE.blocks(true));
        assertFalse(RedstoneControlMode.IGNORE.blocks(false));
    }

    @Test
    void ordinalDecodingClampsUntrustedSavedValues() {
        assertEquals(TargetingMode.AUTO, TargetingMode.fromOrdinal(-100));
        assertEquals(TargetingMode.BEACON_WAVE, TargetingMode.fromOrdinal(100));
        assertEquals(TurretAccessMode.PRIVATE, TurretAccessMode.fromOrdinal(-1));
        assertEquals(TurretAccessMode.PUBLIC, TurretAccessMode.fromOrdinal(99));
    }
}
