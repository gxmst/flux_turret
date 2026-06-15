package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.util.TurretVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
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
    private int structureCheckCounter = 0;

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

        // Periodically validate structure integrity
        be.structureCheckCounter++;
        if (be.structureCheckCounter % STRUCTURE_CHECK_INTERVAL == 0 || !be.formed) {
            Direction facing = state.hasProperty(GrandCannonBlock.FACING)
                    ? state.getValue(GrandCannonBlock.FACING) : Direction.NORTH;
            boolean wasFormed = be.formed;
            be.formed = be.checkStructureComplete(level, pos, facing);
            if (be.formed != wasFormed) {
                be.markUpdated();
            }
        }

        if (!be.formed) return;

        be.refreshMonsterCacheIfNeeded(level, pos);

        int prevTargetId = be.targetId;
        boolean prevFiring = be.isFiring;
        boolean prevHasEnergy = be.visualHasEnergy;

        Direction signalFacing = state.hasProperty(GrandCannonBlock.FACING)
                ? state.getValue(GrandCannonBlock.FACING) : Direction.NORTH;
        if (be.isStructureRedstoneBlocked(level, pos, signalFacing)) {
            be.targetId = -1;
            be.isFiring = false;
            be.warmupTicks = 0;
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.GRAND_CANNON_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.isFiring != prevFiring || be.visualHasEnergy != prevHasEnergy) {
                be.markUpdated();
            }
            return;
        }

        int fireCost = TurretConfig.GRAND_CANNON_FIRE_COST.get();
        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= fireCost;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0)
            be.attackCooldown--;

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
                        be.fireCannon(level, pos, target);
                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = TurretConfig.GRAND_CANNON_COOLDOWN.get();
                        be.warmupTicks = 0;
                        be.sendFirePacket();
                    }
                } else {
                    be.isFiring = false;
                }
            } else {
                be.isFiring = false;
            }
        }

        if (be.targetId != prevTargetId || be.isFiring != prevFiring
                || be.visualHasEnergy != prevHasEnergy) {
            be.markUpdated();
        }
    }

    private boolean isStructureRedstoneBlocked(Level level, BlockPos corePos, Direction facing) {
        for (GrandCannonBlock.CannonPart part : GrandCannonBlock.CannonPart.values()) {
            if (level.hasNeighborSignal(part.offset(corePos, facing))) {
                return true;
            }
        }
        return false;
    }

    private void fireCannon(Level level, BlockPos pos, Monster target) {
        Direction facing = getBlockState().getValue(GrandCannonBlock.FACING);
        Vec3 muzzlePos = new Vec3(
                pos.getX() + 0.5 + facing.getStepX() * 1.5 + facing.getClockWise().getStepX() * 0.5,
                pos.getY() + 1.2,
                pos.getZ() + 0.5 + facing.getStepZ() * 1.5 + facing.getClockWise().getStepZ() * 0.5);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);

        // Play cannon fire sound (vanilla, lower volume)
        TurretVisualEffects.playTurretSound(level, pos, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f, 0.1f);

        // Recoil smoke from barrel
        Vec3 backDirection = new Vec3(-facing.getStepX(), 0, -facing.getStepZ());
        TurretVisualEffects.spawnCannonRecoilSmoke(level, muzzlePos, backDirection);

        // Spawn parabolic particle trail (server-side)
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            double horizontalDist = Math.sqrt((targetPos.x - muzzlePos.x) * (targetPos.x - muzzlePos.x) + (targetPos.z - muzzlePos.z) * (targetPos.z - muzzlePos.z));
            double arcHeight = Math.max(6.0, horizontalDist * 0.15);
            int steps = Math.max(8, Math.min(30, (int) horizontalDist / 2));
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / steps;
                double x = muzzlePos.x + (targetPos.x - muzzlePos.x) * t;
                double z = muzzlePos.z + (targetPos.z - muzzlePos.z) * t;
                double baseY = muzzlePos.y + (targetPos.y - muzzlePos.y) * t;
                double y = baseY + arcHeight * 4.0 * t * (1.0 - t);
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
                if (i % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        }

        // Area damage at target position
        double explosionRadius = TurretConfig.GRAND_CANNON_EXPLOSION_RADIUS.get();
        AABB damageArea = new AABB(
                targetPos.x - explosionRadius, targetPos.y - explosionRadius, targetPos.z - explosionRadius,
                targetPos.x + explosionRadius, targetPos.y + explosionRadius, targetPos.z + explosionRadius);

        List<Monster> monstersInArea = level.getEntitiesOfClass(Monster.class, damageArea, m -> {
            if (!m.isAlive()) return false;
            if (TurretConfig.FRIENDLY_FIRE_PROTECTION.get() && m.hasCustomName()) return false;
            return m.position().distanceTo(targetPos) <= explosionRadius;
        });

        float damage = TurretConfig.GRAND_CANNON_DAMAGE.get().floatValue();
        for (Monster monster : monstersInArea) {
            // Reset invulnerability to ensure damage is applied
            monster.invulnerableTime = 0;
            monster.hurt(level.damageSources().explosion(null, null), damage);
            Vec3 knockDir = monster.position().subtract(targetPos).normalize();
            monster.setDeltaMovement(monster.getDeltaMovement().add(knockDir.x * 1.5, 0.5, knockDir.z * 1.5));
        }

        // Enhanced Red Alert style explosion
        TurretVisualEffects.spawnCannonExplosion(level, targetPos, (float) explosionRadius);

        // Screen shake for nearby players
        TurretVisualEffects.createScreenShake(level, BlockPos.containing(targetPos), 0.5f, 32);

        // Impact sound (vanilla, moderate volume)
        TurretVisualEffects.playTurretSound(level, BlockPos.containing(targetPos),
            SoundEvents.GENERIC_EXPLODE, 1.0f, 0.8f, 0.15f);
    }

    @Override
    protected boolean isValidTarget(Monster monster, Level level, BlockPos selfPos) {
        if (!monster.isAlive()) return false;
        if (TurretConfig.FRIENDLY_FIRE_PROTECTION.get() && monster.hasCustomName()) return false;
        // Grand Cannon uses arcing bombardment - no line-of-sight required.
        // Only skip targets that are deep underground (no sky access within 4 blocks above).
        return level.canSeeSky(monster.blockPosition().above(4));
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
