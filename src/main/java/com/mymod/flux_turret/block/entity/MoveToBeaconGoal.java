package com.mymod.flux_turret.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

public class MoveToBeaconGoal extends Goal {
    private static final int PATH_RECALC_INTERVAL = 40;
    private static final int UNLOADED_BEACON_GRACE_TICKS = 200;
    private static final int MISSING_BEACON_GRACE_TICKS = 20;

    private final Mob mob;
    private final BlockPos targetPos;
    private final double speedModifier;
    private int recalcCooldown;
    private int missingBeaconTicks;

    public MoveToBeaconGoal(Mob mob, BlockPos targetPos, double speedModifier) {
        this.mob = mob;
        this.targetPos = targetPos;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    @Override
    public boolean canUse() {
        if (mob == null || !mob.isAlive()) return false;
        if (!mob.level().hasChunkAt(targetPos)) {
            if (++missingBeaconTicks > UNLOADED_BEACON_GRACE_TICKS) mob.discard();
            return false;
        }
        BlockEntity be = mob.level().getBlockEntity(targetPos);
        if (be instanceof PsychicBeaconBlockEntity beacon) {
            if (beacon.getBeaconState() == PsychicBeaconBlockEntity.STATE_ACTIVE) {
                missingBeaconTicks = 0;
                return true;
            }
            if (beacon.getBeaconState() == PsychicBeaconBlockEntity.STATE_WARNING) {
                missingBeaconTicks = 0;
                return false;
            }
            mob.discard();
            return false;
        }
        if (++missingBeaconTicks > MISSING_BEACON_GRACE_TICKS) mob.discard();
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(targetPos.getX() + 0.5D, targetPos.getY() + 1.0D, targetPos.getZ() + 0.5D, speedModifier);
        recalcCooldown = 0;
    }

    @Override
    public void tick() {
        recalcCooldown--;
        if (recalcCooldown <= 0) {
            double targetX = targetPos.getX() + 0.5D;
            double targetY = targetPos.getY() + 1.0D;
            double targetZ = targetPos.getZ() + 0.5D;
            if (mob.distanceToSqr(targetX, targetY, targetZ) > 4.0D) {
                mob.getNavigation().moveTo(targetX, targetY, targetZ, speedModifier);
            } else {
                mob.getNavigation().stop();
            }
            recalcCooldown = PATH_RECALC_INTERVAL;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
