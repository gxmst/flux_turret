package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class GatlingTurretBlockEntity extends TurretBlockEntityBase {
    private static final int CAPACITY = 60000;
    private static final int MAX_RECEIVE = 800;
    private static final int FIRE_COST = 30;
    private static final double TARGET_RANGE = 11.0;
    private static final float DAMAGE = 0.5f;
    private static final int MIN_FIRE_INTERVAL = 2;
    private static final int MAX_FIRE_INTERVAL = 30;
    private static final int MAX_SPIN = 200;
    private static final int MIN_SPIN_TO_FIRE = 30;
    private static final int TARGET_CACHE_INTERVAL = 6;
    private static final int SOUND_INTERVAL = 4;

    private int spinUp = 0;
    private long lastSoundTime = 0;

    public GatlingTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GATLING_TURRET_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    @Override
    protected double getTargetRange() {
        return TARGET_RANGE;
    }

    @Override
    protected double getEyeHeight() {
        return 1.4;
    }

    @Override
    protected int getTargetCacheInterval() {
        return TARGET_CACHE_INTERVAL;
    }

    @Override
    protected int getFiringVisualCountdown() {
        return 3;
    }

    @Override
    protected int getMinOperatingCost() {
        return FIRE_COST;
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putInt("SpinUp", spinUp);
    }

    @Override
    protected void loadAdditionalTurret(CompoundTag tag) {
        spinUp = tag.getInt("SpinUp");
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GatlingTurretBlockEntity be) {
        if (level.isClientSide) {
            be.baseClientTick(level);
            return;
        }

        be.refreshMonsterCacheIfNeeded(level, pos);

        int prevTargetId = be.targetId;
        int prevSpinUp = be.spinUp;
        boolean prevFiring = be.isFiring;
        long prevFireTime = be.lastFireTime;
        boolean prevHasEnergy = be.visualHasEnergy;

        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= FIRE_COST;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0)
            be.attackCooldown--;

        Monster target = hasEnoughEnergy ? be.findClosestMonster(level, pos) : null;

        if (target == null) {
            be.targetId = -1;
            be.isFiring = false;
            be.spinUp = Math.max(0, be.spinUp - 4);
        } else {
            be.targetId = target.getId();
            be.spinUp = Math.min(MAX_SPIN, be.spinUp + 3);

            if (be.spinUp < MIN_SPIN_TO_FIRE) {
                be.isFiring = false;
            } else if (be.attackCooldown <= 0 && be.getEnergyStorage().consumeEnergy(FIRE_COST)) {
                int interval = getFireInterval(be.spinUp);
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().magic(), DAMAGE);
                if (level.getGameTime() - be.lastSoundTime >= SOUND_INTERVAL) {
                    level.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 0.45f, 1.75f);
                    level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.25f, 1.35f);
                    be.lastSoundTime = level.getGameTime();
                }
                be.isFiring = true;
                be.lastFireTime = level.getGameTime();
                be.attackCooldown = interval;
            } else {
                be.isFiring = false;
            }
        }

        if (be.targetId != prevTargetId || be.spinUp != prevSpinUp || be.isFiring != prevFiring
                || be.lastFireTime != prevFireTime || be.visualHasEnergy != prevHasEnergy) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static int getFireInterval(int spinUp) {
        float t = Math.max(0, Math.min(MAX_SPIN, spinUp)) / (float) MAX_SPIN;
        float curved = t * t;
        return Math.max(MIN_FIRE_INTERVAL, Math.round(MAX_FIRE_INTERVAL + (MIN_FIRE_INTERVAL - MAX_FIRE_INTERVAL) * curved));
    }

    public int getSpinUp() {
        return spinUp;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(12, 8, 12);
    }
}
