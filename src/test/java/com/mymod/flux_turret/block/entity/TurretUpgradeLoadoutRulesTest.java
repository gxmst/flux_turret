package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.item.TurretUpgradeLoadout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurretUpgradeLoadoutRulesTest {
    @Test
    void wrongSlotSavedBitFallsBackToFirstEligibleModule() {
        int weapon = TurretUpgradeType.ARMOR_PIERCING_ROUNDS.getMask();
        int utility = TurretUpgradeType.SLOW_ROUNDS.getMask();

        assertEquals(weapon, TurretUpgradeLoadout.normalizeActiveMask(
                utility, weapon | utility, TurretUpgradeType.Slot.WEAPON, type -> true));
    }

    @Test
    void unsupportedInstalledBitCannotRemainActive() {
        int supported = TurretUpgradeType.ARMOR_PIERCING_ROUNDS.getMask();
        int unsupported = TurretUpgradeType.CHAIN_JUMP.getMask();

        assertEquals(supported, TurretUpgradeLoadout.normalizeActiveMask(
                unsupported, supported | unsupported, TurretUpgradeType.Slot.WEAPON,
                type -> type == TurretUpgradeType.ARMOR_PIERCING_ROUNDS));
    }

    @Test
    void multipleOrMissingSelectionsNormalizeDeterministically() {
        int first = TurretUpgradeType.ARMOR_PIERCING_ROUNDS.getMask();
        int second = TurretUpgradeType.FIRE_ROUNDS.getMask();
        int installed = first | second;

        assertEquals(first, TurretUpgradeLoadout.normalizeActiveMask(
                installed, installed, TurretUpgradeType.Slot.WEAPON, type -> true));
        assertEquals(0, TurretUpgradeLoadout.normalizeActiveMask(
                first, 0, TurretUpgradeType.Slot.WEAPON, type -> true));
    }
}
