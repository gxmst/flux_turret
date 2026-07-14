package com.mymod.flux_turret.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.PushReaction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretStructureBlockTest {
    @Test
    void everyCannonPartResolvesBackToItsCore() {
        BlockPos core = new BlockPos(13, 72, -9);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> occupied = new HashSet<>();
            for (GrandCannonBlock.CannonPart part : GrandCannonBlock.CannonPart.values()) {
                BlockPos partPos = part.offset(core, facing);
                assertEquals(core, part.getCorePos(partPos, facing));
                occupied.add(partPos);
            }
            assertEquals(GrandCannonBlock.CannonPart.values().length, occupied.size());
        }
    }

    @Test
    void extensionCellsAreSelectableButRemainNonCollidingAndImmovable() {
        BlockPos pos = new BlockPos(4, 66, 8);

        assertFalse(TurretExtensionRules.outlineShape().isEmpty());
        assertTrue(TurretExtensionRules.collisionShape().isEmpty());
        assertEquals(PushReaction.BLOCK, TurretExtensionRules.pistonReaction());
        assertEquals(pos.below(2), TurretExtensionRules.corePosForOffset(pos, 2));
    }
}
