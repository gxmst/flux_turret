package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.util.TurretVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TeslaCoilBlockEntity extends TurretBlockEntityBase {
    private static final int MAX_RECEIVE = 1200;
    private static final int WARMUP_TICKS = 8;
    private static final int ATTACK_COOLDOWN = 24;
    private static final int TARGET_CACHE_INTERVAL = 12;
    private static final int CHAIN_JUMP_LIMIT = 5;
    private static final double CHAIN_JUMP_RANGE = 9.0;
    private static final double OVERLOAD_BURST_RANGE = 4.5;

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
    public boolean canInstallUpgrade(TurretUpgradeType type) {
        return type == TurretUpgradeType.CHAIN_JUMP
                || type == TurretUpgradeType.EMP_SLOW
                || type == TurretUpgradeType.OVERLOAD_BURST;
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

        markUpdated();
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

        int prevTargetId = be.targetId;
        boolean prevFiring = be.isFiring;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            be.targetId = -1;
            be.isFiring = false;
            be.warmupTicks = 0;
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.TESLA_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.isFiring != prevFiring || be.visualHasEnergy != prevHasEnergy) {
                be.markUpdated();
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

        if (hasEnoughEnergy) {
            be.refreshMonsterCacheIfNeeded(level, pos);
        } else {
            be.monsterCache = java.util.List.of();
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
                        // Reset invulnerability to ensure damage is applied
                        target.invulnerableTime = 0;
                        target.hurt(level.damageSources().magic(), finalDamage);
                        if (be.hasUpgrade(TurretUpgradeType.EMP_SLOW)) {
                            be.applyEmpSlow(target);
                        }
                        if (be.hasUpgrade(TurretUpgradeType.CHAIN_JUMP)) {
                            be.chainLightning(level, target, finalDamage * 0.65f);
                        }
                        if (be.hasUpgrade(TurretUpgradeType.OVERLOAD_BURST)) {
                            be.overloadBurst(level, target, finalDamage * 0.35f);
                        }

                        // Enhanced Red Alert style electric arc
                        Vec3 coilTop = Vec3.atCenterOf(pos).add(0, 2.5, 0);
                        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                        TurretVisualEffects.spawnElectricArc(level, coilTop, targetPos);

                        // Sound with pitch variation based on overcharge
                        float pitch = be.isOvercharged() ? 1.3f : 1.0f;
                        TurretVisualEffects.playTurretSound(level, pos, ModRegistry.TESLA_SHOOT.get(),
                            0.75f, pitch, 0.15f);

                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = ATTACK_COOLDOWN;
                        be.warmupTicks = 0;
                        be.sendFirePacket();
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
                || be.visualHasEnergy != prevHasEnergy
                || prevOvercharged != nowOvercharged) {
            be.markUpdated();
        }
    }

    private void chainLightning(Level level, Monster primaryTarget, float damage) {
        java.util.Set<Integer> hitIds = new java.util.HashSet<>();
        hitIds.add(primaryTarget.getId());
        Vec3 previous = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.5, 0);

        for (int i = 0; i < CHAIN_JUMP_LIMIT; i++) {
            Vec3 jumpOrigin = previous;
            AABB chainArea = new AABB(jumpOrigin, jumpOrigin).inflate(CHAIN_JUMP_RANGE);
            Monster next = level.getEntitiesOfClass(Monster.class, chainArea, monster ->
                    !hitIds.contains(monster.getId()) && monster.isAlive() && !monster.isRemoved()
                            && monster.position().distanceTo(jumpOrigin) <= CHAIN_JUMP_RANGE
                            && (!TurretConfig.FRIENDLY_FIRE_PROTECTION.get() || !monster.hasCustomName()))
                    .stream()
                    .min(java.util.Comparator.comparingDouble(monster -> monster.position().distanceToSqr(jumpOrigin)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            hitIds.add(next.getId());
            next.invulnerableTime = 0;
            next.hurt(level.damageSources().magic(), damage);
            if (hasUpgrade(TurretUpgradeType.EMP_SLOW)) {
                applyEmpSlow(next);
            }
            Vec3 nextPos = next.position().add(0, next.getBbHeight() * 0.5, 0);
            TurretVisualEffects.spawnElectricArc(level, previous, nextPos);
            previous = nextPos;
            damage *= 0.72f;
        }
    }

    private void overloadBurst(Level level, Monster primaryTarget, float damage) {
        Vec3 center = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.5, 0);
        AABB burstArea = primaryTarget.getBoundingBox().inflate(OVERLOAD_BURST_RANGE);
        java.util.List<Monster> burstTargets = level.getEntitiesOfClass(Monster.class, burstArea, monster ->
                monster != primaryTarget && monster.isAlive() && !monster.isRemoved()
                        && monster.position().distanceTo(center) <= OVERLOAD_BURST_RANGE
                        && (!TurretConfig.FRIENDLY_FIRE_PROTECTION.get() || !monster.hasCustomName()));
        for (Monster monster : burstTargets) {
            monster.invulnerableTime = 0;
            monster.hurt(level.damageSources().magic(), damage);
            applyEmpSlow(monster);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                    32, OVERLOAD_BURST_RANGE * 0.35, 0.45, OVERLOAD_BURST_RANGE * 0.35, 0.08);
        }
    }

    private void applyEmpSlow(Monster monster) {
        monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, true, true));
        monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, true, true));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(24, 15, 24);
    }
}
