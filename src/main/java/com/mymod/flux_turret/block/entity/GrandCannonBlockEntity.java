package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.util.TurretVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class GrandCannonBlockEntity extends TurretBlockEntityBase {
    private static final int MAX_RECEIVE = 2000;
    private static final int WARMUP_TICKS = 40;
    private static final int TARGET_CACHE_INTERVAL = 20;
    private static final int STRUCTURE_CHECK_INTERVAL = 100;

    private int warmupTicks = 0;
    private boolean formed = false;

    public GrandCannonBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GRAND_CANNON_BE.get(), pos, state,
                isCorePart(state) ? TurretConfig.GRAND_CANNON_CAPACITY.get() : 1,
                isCorePart(state) ? MAX_RECEIVE : 0);
    }

    private static boolean isCorePart(BlockState state) {
        return state.hasProperty(GrandCannonBlock.PART)
                && state.getValue(GrandCannonBlock.PART) == GrandCannonBlock.CannonPart.BACK_LEFT;
    }
    
    private boolean isCore() {
        if (!this.getBlockState().hasProperty(GrandCannonBlock.PART)) return false;
        return this.getBlockState().getValue(GrandCannonBlock.PART) == GrandCannonBlock.CannonPart.BACK_LEFT;
    }

    @Override
    public @org.jetbrains.annotations.NotNull <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(@org.jetbrains.annotations.NotNull net.minecraftforge.common.capabilities.Capability<T> cap, @org.jetbrains.annotations.Nullable Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY && !isCore()) {
            if (level != null && this.getBlockState().hasProperty(GrandCannonBlock.PART) && this.getBlockState().hasProperty(GrandCannonBlock.FACING)) {
                GrandCannonBlock.CannonPart part = this.getBlockState().getValue(GrandCannonBlock.PART);
                Direction facing = this.getBlockState().getValue(GrandCannonBlock.FACING);
                BlockPos corePos = part.getCorePos(this.getBlockPos(), facing);
                net.minecraft.world.level.block.entity.BlockEntity coreBe = level.getBlockEntity(corePos);
                if (coreBe != null) {
                    return coreBe.getCapability(cap, side);
                }
            }
        }
        return super.getCapability(cap, side);
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
        this.setChanged();
    }

    public boolean isFormed() {
        return formed;
    }

    public int getEnergyCapacity() {
        return TurretConfig.GRAND_CANNON_CAPACITY.get();
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 0, state -> {
            // Only core block plays animations
            if (!isCore()) return software.bernie.geckolib.core.object.PlayState.STOP;
            if (this.isVisuallyPowered()) {
                if (this.visualCountdown > 0) {
                    return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.grand_cannon.active"));
                }
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.grand_cannon.idle"));
            }
            return software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    protected double getTargetRange() {
        return TurretConfig.GRAND_CANNON_RANGE.get();
    }

    @Override
    protected double getEyeHeight() {
        return 1.5;
    }

    @Override
    protected int getTargetCacheInterval() {
        return TARGET_CACHE_INTERVAL;
    }

    @Override
    protected int getFiringVisualCountdown() {
        // Must cover the full 2.0s (40-tick) recoil clip; otherwise the controller
        // reverts to idle mid-recoil and the gun/barrels snap back to rest, so the
        // kick is barely visible. 40 ticks = one complete recoil, then clean idle.
        return 40;
    }

    @Override
    protected int getMinOperatingCost() {
        return TurretConfig.GRAND_CANNON_FIRE_COST.get();
    }

    @Override
    protected boolean isPerformanceTrackedTurret() {
        return isCore();
    }

    @Override
    protected TargetingMode getAutomaticTargetingMode() {
        return TargetingMode.CLUSTER;
    }

    @Override
    protected boolean isStructureValidForDiagnostics() {
        return formed;
    }

    @Override
    protected boolean isWarmingUpForDiagnostics() {
        return warmupTicks > 0;
    }

    @Override
    public boolean canInstallUpgrade(TurretUpgradeType type) {
        return isCore() && (type == TurretUpgradeType.SEISMIC_SHOCK
                || type == TurretUpgradeType.ARMOR_BREAK
                || type == TurretUpgradeType.CLUSTER_SHELLS);
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putBoolean("Formed", formed);
        tag.putInt("WarmupTicks", warmupTicks);
    }

    @Override
    protected void loadAdditionalTurret(CompoundTag tag) {
        formed = tag.getBoolean("Formed");
        warmupTicks = tag.getInt("WarmupTicks");
    }

    @Override
    protected void handleDataPacketAdditional(CompoundTag tag) {
    }

    /**
     * Validate that all 4 parts of the 2x2x1 structure exist.
     */
    private boolean checkStructureComplete(Level level, BlockPos pos, Direction facing) {
        for (GrandCannonBlock.CannonPart part : GrandCannonBlock.CannonPart.values()) {
            BlockPos partPos = part.offset(pos, facing);
            BlockState partState = level.getBlockState(partPos);
            if (!partState.hasProperty(GrandCannonBlock.PART)) return false;
            if (partState.getValue(GrandCannonBlock.PART) != part) return false;
            if (!partState.hasProperty(GrandCannonBlock.FACING)) return false;
            if (partState.getValue(GrandCannonBlock.FACING) != facing) return false;
        }
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GrandCannonBlockEntity be) {
        if (level.isClientSide) {
            be.baseClientTick(level);
            return;
        }

        if (!state.hasProperty(GrandCannonBlock.PART) || state.getValue(GrandCannonBlock.PART) != GrandCannonBlock.CannonPart.BACK_LEFT) {
            return;
        }

        be.flushThrottledUpdate();

        // Periodically validate structure integrity
        if (!be.formed || isPositionScheduledTick(level.getGameTime(), pos, STRUCTURE_CHECK_INTERVAL)) {
            Direction facing = state.hasProperty(GrandCannonBlock.FACING)
                    ? state.getValue(GrandCannonBlock.FACING) : Direction.NORTH;
            boolean wasFormed = be.formed;
            be.formed = be.checkStructureComplete(level, pos, facing);
            if (be.formed != wasFormed) {
                if (!be.formed) {
                    be.targetId = -1;
                    be.isFiring = false;
                    be.warmupTicks = 0;
                    be.clearAimTarget();
                }
                be.markUpdated();
            }
        }

        if (!be.formed) {
            if (be.targetId != -1 || be.isFiring || be.warmupTicks != 0 || be.hasAimTarget()) {
                be.targetId = -1;
                be.isFiring = false;
                be.warmupTicks = 0;
                be.clearAimTarget();
                be.requestThrottledUpdate();
                be.flushThrottledUpdate();
            }
            return;
        }

        int prevTargetId = be.targetId;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            be.targetId = -1;
            be.isFiring = false;
            be.warmupTicks = 0;
            be.clearAimTarget();
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.GRAND_CANNON_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.visualHasEnergy != prevHasEnergy) {
                be.requestThrottledUpdate();
            }
            be.flushThrottledUpdate();
            return;
        }

        int fireCost = TurretConfig.GRAND_CANNON_FIRE_COST.get();
        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= fireCost;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0) {
            be.attackCooldown--;
            if (be.attackCooldown == 0 || isPositionScheduledTick(level.getGameTime(), pos, 20)) be.setChanged();
        }

        // Skip the (expensive, range-64) monster scan entirely when we can't afford
        // to fire — matches the Gatling/Tesla energy gating and avoids a full
        // getEntitiesOfClass over a 128-block cube every cache interval while idle.
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
            be.clearAimTarget();
        } else {
            be.targetId = target.getId();
            be.setAimTarget(target.getX(), target.getEyeY(), target.getZ());
            if (be.attackCooldown <= 0) {
                be.warmupTicks++;
                if (isPositionScheduledTick(level.getGameTime(), pos, 20)) be.setChanged();
                if (be.warmupTicks >= WARMUP_TICKS) {
                    if (be.getEnergyStorage().consumeEnergy(fireCost)) {
                        Vec3 impactPos = be.fireCannon(level, pos, target);
                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = TurretConfig.GRAND_CANNON_COOLDOWN.get();
                        be.warmupTicks = 0;
                        be.setChanged();
                        be.sendFirePacket(impactPos, List.of(),
                                be.hasUpgrade(TurretUpgradeType.CLUSTER_SHELLS)
                                        ? TurretVisualEffects.EFFECT_CLUSTER_SHELLS : 0,
                                TurretConfig.GRAND_CANNON_EXPLOSION_RADIUS.get().floatValue());
                    }
                } else {
                    be.isFiring = false;
                }
            } else {
                be.isFiring = false;
            }
        }

        if (be.targetId != prevTargetId || be.visualHasEnergy != prevHasEnergy
                || be.aimDriftedSinceSync()) {
            be.requestThrottledUpdate();
        }
        be.flushThrottledUpdate();
    }

    @Override
    protected boolean hasRedstoneSignal(Level level, BlockPos corePos) {
        BlockState coreState = level.getBlockState(corePos);
        Direction facing = coreState.hasProperty(GrandCannonBlock.FACING)
                ? coreState.getValue(GrandCannonBlock.FACING) : Direction.NORTH;
        for (GrandCannonBlock.CannonPart part : GrandCannonBlock.CannonPart.values()) {
            if (level.hasNeighborSignal(part.offset(corePos, facing))) return true;
        }
        return false;
    }

    private Vec3 fireCannon(Level level, BlockPos pos, Mob target) {
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);

        // Play cannon fire sound (vanilla, lower volume)
        TurretVisualEffects.playTurretSound(level, pos, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f, 0.1f);

        // Resolve the main blast and all cluster overlaps into one hit per mob.
        // Applying sub-blasts sequentially makes the first hit's vanilla hurt
        // cooldown swallow every lower-damage cluster hit.
        double explosionRadius = TurretConfig.GRAND_CANNON_EXPLOSION_RADIUS.get();
        boolean clusterUpgrade = hasUpgrade(TurretUpgradeType.CLUSTER_SHELLS);
        double clusterRadius = Math.max(2.0, explosionRadius * 0.45);
        List<Vec3> clusterCenters = clusterUpgrade
                ? createClusterCenters(targetPos, clusterRadius)
                : List.of();
        double damageExtent = Math.max(explosionRadius, clusterUpgrade ? clusterRadius * 2.0 : 0.0);
        AABB damageArea = new AABB(
                targetPos.x - damageExtent, targetPos.y - damageExtent, targetPos.z - damageExtent,
                targetPos.x + damageExtent, targetPos.y + damageExtent, targetPos.z + damageExtent);

        List<Mob> monstersInArea = trackedEntityQuery(level, Mob.class, damageArea,
                TurretBlockEntityBase::isEnemyTarget);

        float damage = TurretConfig.GRAND_CANNON_DAMAGE.get().floatValue();
        for (Mob monster : monstersInArea) {
            Vec3 monsterCenter = monster.position().add(0.0, monster.getBbHeight() * 0.5, 0.0);
            float resolvedDamage = monsterCenter.distanceTo(targetPos) <= explosionRadius ? damage : 0.0f;
            for (Vec3 clusterCenter : clusterCenters) {
                if (monsterCenter.distanceTo(clusterCenter) <= clusterRadius) {
                    resolvedDamage += damage * 0.35f;
                }
            }
            if (resolvedDamage <= 0.0f) continue;

            if (hasUpgrade(TurretUpgradeType.ARMOR_BREAK)) {
                float armorCrackDamage = Math.min(18.0f,
                        resolvedDamage * 0.18f + monster.getArmorValue() * 1.35f);
                resolvedDamage += armorCrackDamage;
                monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0, true, true));
            }
            monster.hurt(level.damageSources().explosion(null, null), resolvedDamage);
            Vec3 knockDir = monsterCenter.subtract(targetPos).normalize();
            monster.setDeltaMovement(monster.getDeltaMovement().add(knockDir.x * 1.5, 0.5, knockDir.z * 1.5));
            if (hasUpgrade(TurretUpgradeType.SEISMIC_SHOCK)) {
                monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1, true, true));
                monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, true));
            }
        }

        // Impact sound (vanilla, moderate volume)
        TurretVisualEffects.playTurretSound(level, BlockPos.containing(targetPos),
            SoundEvents.GENERIC_EXPLODE, 1.0f, 0.8f, 0.15f);
        return targetPos;
    }

    private static List<Vec3> createClusterCenters(Vec3 targetPos, double clusterRadius) {
        return List.of(
                targetPos.add(clusterRadius, 0.0, 0.0),
                targetPos.add(-clusterRadius, 0.0, 0.0),
                targetPos.add(0.0, 0.0, clusterRadius),
                targetPos.add(0.0, 0.0, -clusterRadius));
    }

    @Override
    protected boolean isValidTarget(Mob monster, Level level, BlockPos selfPos) {
        if (!isTargetUsable(monster, selfPos)) return false;
        double minRange = Math.min(TurretConfig.GRAND_CANNON_MIN_RANGE.get(),
                TurretConfig.GRAND_CANNON_RANGE.get());
        double dx = monster.getX() - (selfPos.getX() + 0.5D);
        double dz = monster.getZ() - (selfPos.getZ() + 0.5D);
        if (dx * dx + dz * dz < minRange * minRange) return false;
        // The shell follows an arc, so horizontal line of sight is irrelevant, but
        // it still needs a short clear vertical approach instead of exploding
        // through a roof simply because the block above that roof can see the sky.
        BlockPos targetPos = monster.blockPosition();
        for (int offset = 1; offset <= 4; offset++) {
            BlockPos approachPos = targetPos.above(offset);
            if (!level.getBlockState(approachPos).getCollisionShape(level, approachPos).isEmpty()) {
                return false;
            }
        }
        return level.canSeeSky(targetPos.above(4));
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction facing = getBlockState().hasProperty(GrandCannonBlock.FACING)
                ? getBlockState().getValue(GrandCannonBlock.FACING) : Direction.NORTH;
        Direction right = facing.getClockWise();
        BlockPos frontRight = worldPosition
                .relative(facing, 1)
                .relative(right, 1);
        return new AABB(
                Math.min(worldPosition.getX(), frontRight.getX()),
                worldPosition.getY(),
                Math.min(worldPosition.getZ(), frontRight.getZ()),
                Math.max(worldPosition.getX(), frontRight.getX()) + 1,
                worldPosition.getY() + 3,
                Math.max(worldPosition.getZ(), frontRight.getZ()) + 1);
    }
}
