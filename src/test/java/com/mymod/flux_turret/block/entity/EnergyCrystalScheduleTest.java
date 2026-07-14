package com.mymod.flux_turret.block.entity;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyCrystalScheduleTest {
    @Test
    void eachCrystalRunsExactlyOncePerFiveTicks() {
        BlockPos pos = new BlockPos(17, 64, -9);
        int runs = 0;
        for (long tick = 0; tick < 25; tick++) {
            if (EnergyCrystalBlockEntity.shouldRunScheduledWork(tick, pos)) {
                runs++;
            }
        }
        assertEquals(5, runs);
    }

    @Test
    void positionsUseStableDifferentPhases() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = first.east();

        assertTrue(EnergyCrystalBlockEntity.shouldRunScheduledWork(2, first));
        assertFalse(EnergyCrystalBlockEntity.shouldRunScheduledWork(2, second));
        assertTrue(EnergyCrystalBlockEntity.shouldRunScheduledWork(1, second));
    }
}
