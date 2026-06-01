package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class TeslaCoilBlockEntity extends TurretBlockEntityBase {
    private static final int MAX_RECEIVE = 1200;
    private static final int WARMUP_TICKS = 8;
    private static final int ATTACK_COOLDOWN = 24;
    private static final int TARGET_CACHE_INTERVAL = 8;

    private int warmupTicks = 0;
    private int overchargeTicks = 0;
    private int manualClicksInWindow = 0;
    private int clickWindowTimer = 0;

    public TeslaCoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TESLA_COIL_BE.get(), pos, state, TurretConfig.TESLA_CAPACITY.get(), MAX_RECEIVE);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 0, state -> {
            if (this.isVisuallyPowered()) {
                if (this.isOvercharged()) {
                    return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.tesla_coil.overcharged"));
                }
                if (this.visualCountdown > 0) {
                    return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.tesla_coil.active"));
                }
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.tesla_coil.idle"));
            }
            return software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    protected double getTargetRange() {
        return TurretConfig.TESLA_RANGE.get();
    }

    @Override
    protected double getEyeHeight() {
        return 2.9;
    }

    @Override
    protected int getTargetCacheInterval() {
        return TARGET_CACHE_INTERVAL;
    }

    @Override
    protected int getFiringVisualCountdown() {
        return 7;
    }

    @Override
    protected int getMinOperatingCost() {
        return TurretConfig.TESLA_FIRE_COST.get();
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putInt("OverchargeTicks", overchargeTicks);
    }

    @Override
    protected void loadAdditionalTurret(CompoundTag tag) {
        if (tag.contains("OverchargeTicks")) {
            overchargeTicks = tag.getInt("OverchargeTicks");
        }
    }

    public void performManualCrank() {
        this.getEnergyStorage().receiveEnergy(500, false);

        this.manualClicksInWindow++;
        this.clickWindowTimer = 60;

        if (this.manualClicksInWindow >= 5) {
            this.overchargeTicks = 200;
            this.manualClicksInWindow = 0;

            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.8f);
            }
        }

        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public boolean isOvercharged() {
        return overchargeTicks > 0;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TeslaCoilBlockEntity be) {
        if (level.isClientSide) {
            be.baseClientTick(level);
            return;
        }

        boolean prevOvercharged = be.overchargeTicks > 0;

        if (be.clickWindowTimer > 0) {
            be.clickWindowTimer--;
            if (be.clickWindowTimer <= 0) {
                be.manualClicksInWindow = 0;
            }
        }
        if (be.overchargeTicks > 0) {
            be.overchargeTicks--;
        }

        be.refreshMonsterCacheIfNeeded(level, pos);

        int prevTargetId = be.targetId;
        boolean prevFiring = be.isFiring;
        long prevFireTime = be.lastFireTime;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            be.targetId = -1;
            be.isFiring = false;
            be.warmupTicks = 0;
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.TESLA_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.isFiring != prevFiring || be.visualHasEnergy != prevHasEnergy) {
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }

        int fireCost = TurretConfig.TESLA_FIRE_COST.get();
        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= fireCost;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0) {
            be.attackCooldown -= be.isOvercharged() ? 2 : 1;
            if (be.attackCooldown < 0) be.attackCooldown = 0;
        }

        Monster target = hasEnoughEnergy ? be.findClosestMonster(level, pos) : null;

        if (target == null) {
            be.targetId = -1;
            be.isFiring = false;
            be.warmupTicks = 0;
        } else {
            be.targetId = target.getId();
            if (be.attackCooldown <= 0) {
                be.warmupTicks++;
                if (be.warmupTicks >= WARMUP_TICKS) {
                    if (be.getEnergyStorage().consumeEnergy(fireCost)) {
                        float baseDamage = TurretConfig.TESLA_DAMAGE.get().floatValue();
                        float finalDamage = be.isOvercharged() ? baseDamage * 1.5f : baseDamage;
                        target.invulnerableTime = 0;
                        target.hurt(level.damageSources().magic(), finalDamage);
                        level.playSound(null, pos, ModRegistry.TESLA_SHOOT.get(), SoundSource.BLOCKS, 0.75f, 1.0f);
                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = ATTACK_COOLDOWN;
                        be.warmupTicks = 0;
                    }
                } else {
                    be.isFiring = false;
                    if (be.warmupTicks == 1)
                        level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.35f, 1.9f);
                }
            } else {
                be.isFiring = false;
                be.warmupTicks = 0;
            }
        }

        boolean nowOvercharged = be.overchargeTicks > 0;
        if (be.targetId != prevTargetId || be.isFiring != prevFiring
                || be.lastFireTime != prevFireTime || be.visualHasEnergy != prevHasEnergy
                || prevOvercharged != nowOvercharged) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(24, 15, 24);
    }
}
