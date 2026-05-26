package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class GatlingTurretBlockEntity extends TurretBlockEntityBase {
    private static final int MAX_RECEIVE = 800;
    private static final int MIN_FIRE_INTERVAL = 2;
    private static final int MAX_FIRE_INTERVAL = 30;
    private static final int MAX_SPIN = 200;
    private static final int MIN_SPIN_TO_FIRE = 30;
    private static final int TARGET_CACHE_INTERVAL = 6;
    private static final int SOUND_INTERVAL = 4;

    private int spinUp = 0;
    private long lastSoundTime = 0;

    public GatlingTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GATLING_TURRET_BE.get(), pos, state, TurretConfig.GATLING_CAPACITY.get(), MAX_RECEIVE);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 0, state -> {
            if (this.isVisuallyPowered()) {
                if (this.visualCountdown > 0) {
                    return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.gatling_turret.active"));
                }
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.gatling_turret.idle"));
            }
            return software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    protected double getTargetRange() {
        return TurretConfig.GATLING_RANGE.get();
    }

    @Override
    protected double getEyeHeight() {
        return 1.25;
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
        return TurretConfig.GATLING_FIRE_COST.get();
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

        if (be.isRedstoneBlocked(level, pos)) {
            be.targetId = -1;
            be.isFiring = false;
            be.spinUp = Math.max(0, be.spinUp - 4);
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.GATLING_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.spinUp != prevSpinUp || be.isFiring != prevFiring
                    || be.visualHasEnergy != prevHasEnergy) {
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }

        int fireCost = TurretConfig.GATLING_FIRE_COST.get();
        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= fireCost;
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
            } else if (be.attackCooldown <= 0 && be.getEnergyStorage().consumeEnergy(fireCost)) {
                int interval = getFireInterval(be.spinUp);
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().magic(), TurretConfig.GATLING_DAMAGE.get().floatValue());
                if (level.getGameTime() - be.lastSoundTime >= SOUND_INTERVAL) {
                    level.playSound(null, pos, ModRegistry.GATLING_SHOOT.get(), SoundSource.BLOCKS, 0.45f, 1.0f);
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
