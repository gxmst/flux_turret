package com.mymod.flux_turret.util;

/** Pure timing rules shared by the server crank path and unit tests. */
public final class TeslaCrankRules {
    private TeslaCrankRules() {
    }

    public static boolean isCooldownReady(long gameTime, long lastCrankGameTime, int cooldownTicks) {
        return lastCrankGameTime == Long.MIN_VALUE || gameTime < lastCrankGameTime
                || gameTime - lastCrankGameTime >= Math.max(0, cooldownTicks);
    }
}
