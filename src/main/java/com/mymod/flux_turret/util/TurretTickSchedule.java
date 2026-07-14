package com.mymod.flux_turret.util;

/** Pure position-hash scheduling rules, separated so they can be unit tested without game bootstrap. */
public final class TurretTickSchedule {
    private TurretTickSchedule() {
    }

    public static boolean isScheduledTick(long gameTime, long packedPos, int interval) {
        if (interval <= 1) return true;
        return Math.floorMod(gameTime, (long) interval) == stablePhase(packedPos, interval);
    }

    public static long nextScheduledTick(long gameTime, long packedPos, int interval) {
        if (interval <= 1) return gameTime;
        int phase = stablePhase(packedPos, interval);
        int currentPhase = (int) Math.floorMod(gameTime, (long) interval);
        return gameTime + Math.floorMod(phase - currentPhase, interval);
    }

    public static int stablePhase(long packedPos, int interval) {
        if (interval <= 1) return 0;
        long mixed = packedPos;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) Math.floorMod(mixed, (long) interval);
    }
}
