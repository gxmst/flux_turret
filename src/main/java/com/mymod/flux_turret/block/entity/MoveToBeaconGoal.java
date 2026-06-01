package com.mymod.flux_turret.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

public class MoveToBeaconGoal extends Goal {
    private final Mob mob;
    private final BlockPos targetPos;
    private final double speedModifier;
    private int recalcCooldown;

    public MoveToBeaconGoal(Mob mob, BlockPos targetPos, double speedModifier) {
        this.mob = mob;
        this.targetPos = targetPos;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob == null || !mob.isAlive()) return false;
        BlockEntity be = mob.level().getBlockEntity(targetPos);
        return be instanceof PsychicBeaconBlockEntity beacon && beacon.getBeaconState() == PsychicBeaconBlockEntity.STATE_ACTIVE;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !mob.getNavigation().isDone();
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
            mob.getNavigation().moveTo(targetPos.getX() + 0.5D, targetPos.getY() + 1.0D, targetPos.getZ() + 0.5D, speedModifier);
            recalcCooldown = 20;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
