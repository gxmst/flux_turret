package com.mymod.flux_turret.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretClientConfigTest {
    @Test
    void safeAccessorsUsePresentationPreservingDefaultsBeforeConfigLoad() {
        assertAll(
                () -> assertEquals(TurretClientConfig.EffectQuality.FULL, TurretClientConfig.effectQuality()),
                () -> assertEquals(1.0, TurretClientConfig.particleDensity()),
                () -> assertEquals(4096, TurretClientConfig.particleBudgetPerTick()),
                () -> assertTrue(TurretClientConfig.allowScreenFlashes()),
                () -> assertEquals(0.0, TurretClientConfig.screenShakeStrength()),
                () -> assertTrue(TurretClientConfig.renderTeslaIdleArcs()),
                () -> assertEquals(36.0, TurretClientConfig.teslaIdleArcDistance()),
                () -> assertEquals(24, TurretClientConfig.teslaIdleArcSegments()),
                () -> assertTrue(TurretClientConfig.renderBeaconNetworkLinks()),
                () -> assertEquals(192.0, TurretClientConfig.beaconNetworkLinkDistance()),
                () -> assertEquals(64.0, TurretClientConfig.upgradeGlowDistance()),
                () -> assertFalse(TurretClientConfig.SPEC.isLoaded()));
    }
}
