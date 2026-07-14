package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.util.TurretTickSchedule;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretScheduleTest {
    @Test
    void nextScheduledTickNeverAddsAFullIntervalOfLatency() {
        BlockPos pos = new BlockPos(37, 72, -19);
        for (int interval : new int[]{10, 20, 100}) {
            for (long now = 0; now < interval * 3L; now++) {
                long scheduled = TurretTickSchedule.nextScheduledTick(now, pos.asLong(), interval);
                assertTrue(scheduled >= now);
                assertTrue(scheduled < now + interval);
                assertTrue(TurretTickSchedule.isScheduledTick(scheduled, pos.asLong(), interval));
            }
        }
    }

    @Test
    void phasesAreStableAndSpreadAcrossNearbyTowers() {
        Set<Integer> phases = new HashSet<>();
        for (int x = 0; x < 32; x++) {
            BlockPos pos = new BlockPos(x, 64, x / 2);
            int first = TurretTickSchedule.stablePhase(pos.asLong(), 20);
            int second = TurretTickSchedule.stablePhase(pos.asLong(), 20);
            assertEquals(first, second);
            phases.add(first);
        }
        assertTrue(phases.size() >= 12, "nearby towers should not collapse onto a few phases");
    }

    @Test
    void onePositionRunsExactlyOncePerInterval() {
        BlockPos pos = new BlockPos(-4, 80, 91);
        for (int interval : new int[]{10, 20, 100}) {
            int runs = 0;
            for (long tick = 0; tick < interval * 4L; tick++) {
                if (TurretTickSchedule.isScheduledTick(tick, pos.asLong(), interval)) runs++;
            }
            assertEquals(4, runs);
        }
    }
}
