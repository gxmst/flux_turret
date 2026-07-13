package com.mymod.flux_turret.block.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatlingDamageBatchTest {
    @Test
    void shotsAccumulateWithoutMovingTheSettlementWindow() {
        GatlingTurretBlockEntity.DamageBatch batch =
                new GatlingTurretBlockEntity.DamageBatch(UUID.randomUUID());

        batch.addShot(2.0f, 100L);
        batch.addShot(3.5f, 104L);

        assertEquals(2, batch.shotCount);
        assertEquals(5.5f, batch.damage);
        assertEquals(110L, batch.settleAt);
        assertFalse(batch.isDue(109L));
        assertTrue(batch.isDue(110L));
    }

    @Test
    void emptyBatchNeverSettles() {
        GatlingTurretBlockEntity.DamageBatch batch =
                new GatlingTurretBlockEntity.DamageBatch(UUID.randomUUID());

        assertFalse(batch.isDue(Long.MAX_VALUE));
    }

    @Test
    void paidBatchExpiresEvenWhenItsTargetStillExists() {
        GatlingTurretBlockEntity.DamageBatch batch =
                new GatlingTurretBlockEntity.DamageBatch(UUID.randomUUID());

        batch.addShot(2.0f, 100L);

        assertFalse(batch.isExpired(299L));
        assertTrue(batch.isExpired(300L));
    }

    @Test
    void settlementWaitsForTheNormalDamageWindow() {
        assertFalse(GatlingTurretBlockEntity.DamageBatch.isDamageWindowOpen(11));
        assertTrue(GatlingTurretBlockEntity.DamageBatch.isDamageWindowOpen(10));
        assertTrue(GatlingTurretBlockEntity.DamageBatch.isDamageWindowOpen(0));
    }

    @Test
    void armorIsAppliedPerShotBeforeDamageIsBatched() {
        GatlingTurretBlockEntity.DamageBatch batch =
                new GatlingTurretBlockEntity.DamageBatch(UUID.randomUUID());
        float adjustedShot = GatlingTurretBlockEntity.DamageBatch
                .armorAdjustedShotDamage(2.0f, 20.0f, 8.0f);

        for (int i = 0; i < 5; i++) {
            batch.addShot(adjustedShot, 100L + i * 2L);
        }

        float incorrectlyMerged = GatlingTurretBlockEntity.DamageBatch
                .armorAdjustedShotDamage(10.0f, 20.0f, 8.0f);
        assertEquals(adjustedShot * 5.0f, batch.damage, 0.0001f);
        assertTrue(batch.damage < incorrectlyMerged,
                "merged raw damage would under-count high-armor mitigation");
    }

    @Test
    void legacyRawBatchIsNormalizedPerAverageShot() {
        GatlingTurretBlockEntity.DamageBatch batch =
                new GatlingTurretBlockEntity.DamageBatch(UUID.randomUUID());
        batch.damage = 10.0f;
        batch.shotCount = 5;
        batch.armorAdjusted = false;

        assertTrue(batch.normalizeLegacyDamage(20.0f, 8.0f));
        assertEquals(GatlingTurretBlockEntity.DamageBatch
                .armorAdjustedShotDamage(2.0f, 20.0f, 8.0f) * 5.0f,
                batch.damage, 0.0001f);
        assertTrue(batch.armorAdjusted);
        assertFalse(batch.normalizeLegacyDamage(20.0f, 8.0f));
    }

    @Test
    void firePacketsAreThrottledButTargetSwitchesAreImmediate() {
        GatlingTurretBlockEntity.FirePacketGate gate =
                new GatlingTurretBlockEntity.FirePacketGate();

        assertTrue(gate.onShot(100L, 7));
        assertFalse(gate.onShot(102L, 7));
        assertTrue(gate.onShot(104L, 7));
        assertTrue(gate.onShot(106L, 8));
        assertFalse(gate.onShot(108L, 8));
        assertTrue(gate.onShot(112L, 8));
    }

    @Test
    void gatlingDamageIsNotTaggedToBypassCooldown() throws IOException {
        assertFalse(tagContains("data/minecraft/tags/damage_type/bypasses_cooldown.json",
                "flux_turret:gatling"));
    }

    @Test
    void gatlingDamageRetainsProjectileSemantics() throws IOException {
        assertTrue(tagContains("data/minecraft/tags/damage_type/is_projectile.json",
                "flux_turret:gatling"));
    }

    @Test
    void gatlingBatchBypassesOnlyTheAlreadyAppliedArmorStep() throws IOException {
        assertTrue(tagContains("data/minecraft/tags/damage_type/bypasses_armor.json",
                "flux_turret:gatling"));
        assertFalse(tagContains("data/minecraft/tags/damage_type/bypasses_enchantments.json",
                "flux_turret:gatling"));
        assertFalse(tagContains("data/minecraft/tags/damage_type/bypasses_effects.json",
                "flux_turret:gatling"));
        assertFalse(tagContains("data/minecraft/tags/damage_type/bypasses_resistance.json",
                "flux_turret:gatling"));
        assertFalse(tagContains("data/minecraft/tags/damage_type/bypasses_shield.json",
                "flux_turret:gatling"));
    }

    private boolean tagContains(String path, String expectedValue) throws IOException {
        Enumeration<URL> resources = getClass().getClassLoader().getResources(path);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStreamReader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
                JsonObject tag = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement value : tag.getAsJsonArray("values")) {
                    if (value.isJsonPrimitive() && expectedValue.equals(value.getAsString())) return true;
                }
            }
        }
        return false;
    }
}
