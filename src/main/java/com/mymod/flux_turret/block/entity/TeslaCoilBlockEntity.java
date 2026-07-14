package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.util.TurretVisualEffects;
import com.mymod.flux_turret.util.TeslaCrankRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodData;
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
    private static final int BASE_CHAIN_JUMP_LIMIT = 2;
    private static final double CHAIN_JUMP_RANGE = 9.0;
    private static final double OVERLOAD_BURST_RANGE = 4.5;
    public static final int MANUAL_CRANK_ENERGY = 500;
    public static final int MANUAL_CRANK_COOLDOWN_TICKS = 8;
    private static final float MANUAL_CRANK_SATURATION_COST = 1.0F;
    private static final int MANUAL_CRANK_FOOD_COST = 1;
    private static final int MINIMUM_FOOD_AFTER_CRANK = 6;
    private static final String PLAYER_LAST_CRANK_TAG = "flux_turret:LastManualCrankGameTime";

    private int warmupTicks = 0;
    private int overchargeTicks = 0;
    private int manualClicksInWindow = 0;
    private int clickWindowTimer = 0;
    private long lastManualCrankGameTime = Long.MIN_VALUE;

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
    protected TargetingMode getAutomaticTargetingMode() {
        return TargetingMode.CLUSTER;
    }

    @Override
    protected boolean isWarmingUpForDiagnostics() {
        return warmupTicks > 0;
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

    public ManualCrankResult tryManualCrank(ServerPlayer player) {
        if (level == null || level.isClientSide) return ManualCrankResult.INVALID;

        long gameTime = level.getGameTime();
        CompoundTag playerData = player.getPersistentData();
        boolean playerHasCranked = playerData.contains(PLAYER_LAST_CRANK_TAG, net.minecraft.nbt.Tag.TAG_LONG);
        long playerLastCrank = playerData.getLong(PLAYER_LAST_CRANK_TAG);
        if (!isCrankCooldownReady(gameTime, lastManualCrankGameTime)
                || playerHasCranked && !isCrankCooldownReady(gameTime, playerLastCrank)) {
            return ManualCrankResult.COOLDOWN;
        }

        // Check before taking food. The actual receive happens immediately after
        // payment on the same server thread, so a forged packet cannot gain FE
        // without the resource deduction.
        if (this.getEnergyStorage().receiveEnergy(MANUAL_CRANK_ENERGY, true) <= 0) {
            return ManualCrankResult.FULL;
        }
        if (!consumeCrankResource(player)) return ManualCrankResult.TOO_HUNGRY;

        int received = this.getEnergyStorage().receiveEnergy(MANUAL_CRANK_ENERGY, false);
        if (received <= 0) return ManualCrankResult.FULL;

        lastManualCrankGameTime = gameTime;
        playerData.putLong(PLAYER_LAST_CRANK_TAG, gameTime);

        this.manualClicksInWindow++;
        this.clickWindowTimer = 60;

        if (this.manualClicksInWindow >= 5) {
            this.overchargeTicks = 200;
            this.manualClicksInWindow = 0;

            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.8f);
            }
        }

        this.level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS, 0.7F, 1.5F);
        this.level.playSound(null, worldPosition, SoundEvents.REDSTONE_TORCH_BURNOUT,
                SoundSource.BLOCKS, 0.5F, 1.8F);
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 3.0D,
                    worldPosition.getZ() + 0.5D, 5, 0.3D, 0.75D, 0.3D, 0.02D);
        }
        markUpdated();
        return ManualCrankResult.SUCCESS;
    }

    private static boolean consumeCrankResource(ServerPlayer player) {
        // Creative is an explicit operator/testing exception. It creates FE at no
        // hunger cost, but still passes ownership, context and both rate limits.
        if (player.isCreative()) return true;

        FoodData food = player.getFoodData();
        if (food.getSaturationLevel() >= MANUAL_CRANK_SATURATION_COST) {
            food.setSaturation(food.getSaturationLevel() - MANUAL_CRANK_SATURATION_COST);
            return true;
        }
        if (food.getFoodLevel() - MANUAL_CRANK_FOOD_COST >= MINIMUM_FOOD_AFTER_CRANK) {
            food.setFoodLevel(food.getFoodLevel() - MANUAL_CRANK_FOOD_COST);
            return true;
        }
        return false;
    }

    static boolean isCrankCooldownReady(long gameTime, long lastCrankGameTime) {
        return TeslaCrankRules.isCooldownReady(
                gameTime, lastCrankGameTime, MANUAL_CRANK_COOLDOWN_TICKS);
    }

    public enum ManualCrankResult {
        SUCCESS(null),
        COOLDOWN("message.flux_turret.tesla_crank_cooldown"),
        FULL("message.flux_turret.tesla_energy_full"),
        TOO_HUNGRY("message.flux_turret.tesla_too_hungry"),
        INVALID(null);

        private final String failureTranslationKey;

        ManualCrankResult(String failureTranslationKey) {
            this.failureTranslationKey = failureTranslationKey;
        }

        public String getFailureTranslationKey() {
            return failureTranslationKey;
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

        be.flushThrottledUpdate();

        boolean prevOvercharged = be.overchargeTicks > 0;

        if (be.clickWindowTimer > 0) {
            be.clickWindowTimer--;
            if (be.clickWindowTimer <= 0) {
                be.manualClicksInWindow = 0;
            }
        }
        if (be.overchargeTicks > 0) {
            be.overchargeTicks--;
            if (be.overchargeTicks == 0 || isPositionScheduledTick(level.getGameTime(), pos, 20)) {
                be.setChanged();
            }
        }

        int prevTargetId = be.targetId;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            be.targetId = -1;
            be.isFiring = false;
            be.warmupTicks = 0;
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.TESLA_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.visualHasEnergy != prevHasEnergy) {
                be.requestThrottledUpdate();
            }
            be.flushThrottledUpdate();
            return;
        }

        int fireCost = TurretConfig.TESLA_FIRE_COST.get();
        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= fireCost;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0) {
            be.attackCooldown -= be.isOvercharged() ? 2 : 1;
            if (be.attackCooldown < 0) be.attackCooldown = 0;
            if (be.attackCooldown == 0 || isPositionScheduledTick(level.getGameTime(), pos, 20)) be.setChanged();
        }

        if (hasEnoughEnergy) {
            be.refreshMonsterCacheIfNeeded(level, pos);
        } else {
            be.monsterCache = java.util.List.of();
        }

        Mob target = hasEnoughEnergy ? be.findClosestMonster(level, pos) : null;

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
                        target.hurt(level.damageSources().magic(), finalDamage);
                        if (be.hasUpgrade(TurretUpgradeType.EMP_SLOW)) {
                            be.applyEmpSlow(target);
                        }
                        java.util.Map<Mob, Float> secondaryDamage = new java.util.LinkedHashMap<>();
                        int chainLimit = be.hasUpgrade(TurretUpgradeType.CHAIN_JUMP)
                                ? CHAIN_JUMP_LIMIT : BASE_CHAIN_JUMP_LIMIT;
                        java.util.List<Vec3> chainPoints = be.chainLightning(
                                level, target, finalDamage * 0.65f, secondaryDamage, chainLimit);
                        boolean overloadBurst = be.hasUpgrade(TurretUpgradeType.OVERLOAD_BURST);
                        if (overloadBurst) {
                            be.overloadBurst(level, target, finalDamage * 0.35f, secondaryDamage);
                        }
                        for (java.util.Map.Entry<Mob, Float> hit : secondaryDamage.entrySet()) {
                            hit.getKey().hurt(level.damageSources().magic(), hit.getValue());
                        }

                        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

                        // Sound with pitch variation based on overcharge
                        float pitch = be.isOvercharged() ? 1.3f : 1.0f;
                        TurretVisualEffects.playTurretSound(level, pos, ModRegistry.TESLA_SHOOT.get(),
                            0.75f, pitch, 0.15f);

                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = ATTACK_COOLDOWN;
                        be.warmupTicks = 0;
                        be.setChanged();
                        be.sendFirePacket(targetPos, chainPoints,
                                overloadBurst ? TurretVisualEffects.EFFECT_OVERLOAD_BURST : 0,
                                (float) OVERLOAD_BURST_RANGE);
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
        if (be.targetId != prevTargetId || be.visualHasEnergy != prevHasEnergy
                || prevOvercharged != nowOvercharged) {
            be.requestThrottledUpdate();
        }
        be.flushThrottledUpdate();
    }

    private java.util.List<Vec3> chainLightning(Level level, Mob primaryTarget, float damage,
                                                java.util.Map<Mob, Float> secondaryDamage,
                                                int chainLimit) {
        java.util.List<Vec3> effectPoints = new java.util.ArrayList<>(chainLimit);
        java.util.Set<Integer> hitIds = new java.util.HashSet<>();
        hitIds.add(primaryTarget.getId());
        Vec3 previous = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.5, 0);

        for (int i = 0; i < chainLimit; i++) {
            Vec3 jumpOrigin = previous;
            AABB chainArea = new AABB(jumpOrigin, jumpOrigin).inflate(CHAIN_JUMP_RANGE);
            Mob next = trackedEntityQuery(level, Mob.class, chainArea, monster ->
                    !hitIds.contains(monster.getId()) && isEnemyTarget(monster)
                            && monster.position().distanceTo(jumpOrigin) <= CHAIN_JUMP_RANGE
            )
                    .stream()
                    .min(java.util.Comparator.comparingDouble(monster -> monster.position().distanceToSqr(jumpOrigin)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            hitIds.add(next.getId());
            secondaryDamage.merge(next, damage, Float::sum);
            if (hasUpgrade(TurretUpgradeType.EMP_SLOW)) {
                applyEmpSlow(next);
            }
            Vec3 nextPos = next.position().add(0, next.getBbHeight() * 0.5, 0);
            effectPoints.add(nextPos);
            previous = nextPos;
            damage *= 0.72f;
        }
        return effectPoints;
    }

    private void overloadBurst(Level level, Mob primaryTarget, float damage,
                               java.util.Map<Mob, Float> secondaryDamage) {
        Vec3 center = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.5, 0);
        AABB burstArea = primaryTarget.getBoundingBox().inflate(OVERLOAD_BURST_RANGE);
        java.util.List<Mob> burstTargets = trackedEntityQuery(level, Mob.class, burstArea, monster ->
                monster != primaryTarget && isEnemyTarget(monster)
                        && monster.position().distanceTo(center) <= OVERLOAD_BURST_RANGE
        );
        for (Mob monster : burstTargets) {
            secondaryDamage.merge(monster, damage, Float::sum);
            applyEmpSlow(monster);
        }
    }

    private void applyEmpSlow(Mob monster) {
        monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, true, true));
        monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, true, true));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(24, 15, 24);
    }
}
