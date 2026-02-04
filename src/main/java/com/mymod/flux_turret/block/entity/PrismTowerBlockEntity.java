package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class PrismTowerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Visual Smoothing (Transient)
    public int visualCountdown = 0;
    public int visualTargetType = 0;
    public int visualTargetId = -1;
    public BlockPos visualTargetPos = null;
    public int visualSupportCount = 0;
    public Vec3 visualCachedTargetPos = null;
    public boolean visualHasEnergy = false;

    // Core Logic State
    private int currentDepth = -1;
    private int lastDepth = -1; // Latched from previous tick
    private BlockPos masterPos = null;
    private int targetId = -1;
    private BlockPos targetPos = null;
    private int targetType = 0; // 0: None, 1: Entity, 2: BlockPos

    private int inputCount = 0; // Cumulative inputs this tick
    private int totalTreeSupport = 0; // Total nodes in the tree (for Master)
    private int latchedTreeSupport = 0; // Stable value for damage/render

    private int attackCooldown = 0;
    private int warmupTicks = 0;
    private boolean isFiring = false;
    private long lastFireTime = 0;

    private static class PrismEnergyStorage extends EnergyStorage {
        public PrismEnergyStorage(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }
    }

    private final PrismEnergyStorage energyStorage = new PrismEnergyStorage(100000, 1000) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate)
                setChanged();
            return received;
        }
    };

    private final LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);

    public PrismTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRISM_TOWER_BE.get(), pos, state);
    }

    public void addSupport(BlockPos sourcePos) {
        this.inputCount++;
    }

    public void incrementTotalSupport() {
        this.totalTreeSupport++;
    }

    public int getDepth() {
        return lastDepth;
    }

    public int getInputCount() {
        return inputCount;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PrismTowerBlockEntity be) {
        if (level.isClientSide) {
            if (be.visualCountdown > 0)
                be.visualCountdown--;
            return;
        }

        // --- STEP 1: Sync & Latch ---
        be.latchedTreeSupport = be.totalTreeSupport;
        be.totalTreeSupport = 0;
        be.lastDepth = be.currentDepth;
        be.inputCount = 0;

        boolean hasEnoughEnergy = be.energyStorage.getEnergyStored() >= 1000;
        if (hasEnoughEnergy != be.visualHasEnergy) {
            be.visualHasEnergy = hasEnoughEnergy;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }

        if (be.attackCooldown > 0)
            be.attackCooldown--;

        int prevDepth = be.currentDepth;
        int prevTargetType = be.targetType;
        boolean prevFiring = be.isFiring;
        long prevFireTime = be.lastFireTime;

        // --- STEP 2: Main Logic ---
        if (hasEnoughEnergy) {
            // Find neighbors within 12 blocks
            List<PrismTowerBlockEntity> neighbors = StreamSupport
                    .stream(BlockPos.betweenClosed(pos.offset(-12, -12, -12), pos.offset(12, 12, 12)).spliterator(),
                            false)
                    .map(level::getBlockEntity)
                    .filter(other -> other instanceof PrismTowerBlockEntity && other != be)
                    .map(t -> (PrismTowerBlockEntity) t)
                    .toList();

            // Check for closest monster in range (16)
            AABB monsterScanArea = new AABB(pos).inflate(16.5);
            List<Monster> monsters = level.getEntitiesOfClass(Monster.class, monsterScanArea, LivingEntity::isAlive);
            Monster closestMonster = monsters.stream()
                    .min(Comparator.comparingDouble(m -> m.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                    .orElse(null);

            boolean isMasterPotential = false;
            if (closestMonster != null) {
                double myDistSq = closestMonster.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                // Am I the best master?
                isMasterPotential = neighbors.stream().noneMatch(t -> {
                    double nDistSq = closestMonster.distanceToSqr(t.getBlockPos().getX(), t.getBlockPos().getY(),
                            t.getBlockPos().getZ());
                    if (Math.abs(nDistSq - myDistSq) < 1.0)
                        return t.getBlockPos().hashCode() < pos.hashCode();
                    return nDistSq < myDistSq;
                });
            }

            if (isMasterPotential && closestMonster != null) {
                // I am Master
                be.currentDepth = 0;
                be.masterPos = pos;
                be.targetType = 1;
                be.targetId = closestMonster.getId();
                be.targetPos = null;

                if (be.attackCooldown <= 0) {
                    be.warmupTicks++;
                    if (be.warmupTicks >= 10) {
                        float damage = 10.0f * (float) (1.0 + be.latchedTreeSupport * 0.5);
                        closestMonster.hurt(level.damageSources().magic(), damage);
                        be.energyStorage.extractEnergy(1000, false);
                        level.playSound(null, pos, SoundEvents.GUARDIAN_ATTACK, SoundSource.BLOCKS, 1.0f,
                                1.2f + be.latchedTreeSupport * 0.05f);
                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = 20;
                        be.warmupTicks = 0;
                    } else {
                        be.isFiring = false;
                        if (be.warmupTicks == 1)
                            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 2.0f);
                    }
                } else {
                    be.isFiring = false;
                    be.warmupTicks = 0;
                }
            } else {
                // SLAVE LOGIC: Find a parent purely based on neighbor depth, NOT monster
                // distance
                // This allows chains to extend far beyond monster sight.
                PrismTowerBlockEntity bestParent = neighbors.stream()
                        .filter(t -> t.getDepth() >= 0 && t.getDepth() < 6)
                        .filter(t -> be.lastDepth == -1 || t.getDepth() < be.lastDepth) // Gradient flow
                        .filter(t -> t.getInputCount() < 6)
                        .min(Comparator.comparingInt(t -> t.getDepth()))
                        .orElse(null);

                if (bestParent != null) {
                    be.currentDepth = bestParent.getDepth() + 1;
                    be.masterPos = bestParent.masterPos;
                    be.targetType = 2;
                    be.targetPos = bestParent.getBlockPos();
                    be.targetId = -1;
                    be.warmupTicks = 0;

                    bestParent.addSupport(pos);
                    if (be.masterPos != null) {
                        BlockEntity mBE = level.getBlockEntity(be.masterPos);
                        if (mBE instanceof PrismTowerBlockEntity master)
                            master.incrementTotalSupport();
                    }

                    if (be.attackCooldown <= 0) {
                        be.energyStorage.extractEnergy(500, false);
                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = 2;
                    }
                } else {
                    resetState(be);
                }
            }
        } else {
            resetState(be);
        }

        // --- STEP 3: Sync ---
        if (be.currentDepth != prevDepth || be.targetType != prevTargetType || be.isFiring != prevFiring
                || be.lastFireTime != prevFireTime) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static void resetState(PrismTowerBlockEntity be) {
        be.currentDepth = -1;
        be.masterPos = null;
        be.targetType = 0;
        be.targetId = -1;
        be.targetPos = null;
        be.isFiring = false;
        be.warmupTicks = 0;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(25, 15, 25);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY)
            return energyHandler.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyHandler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Depth", currentDepth);
        tag.putInt("TargetType", targetType);
        tag.putInt("TargetId", targetId);
        tag.putInt("SyncSupports", latchedTreeSupport);
        tag.putBoolean("IsFiring", isFiring);
        tag.putLong("LastFireTime", lastFireTime);
        tag.putBoolean("HasPower", visualHasEnergy);
        if (targetPos != null)
            tag.putLong("TargetPosLong", targetPos.asLong());
        if (masterPos != null)
            tag.putLong("MasterPosLong", masterPos.asLong());
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt("Energy"));
        currentDepth = tag.getInt("Depth");
        targetType = tag.getInt("TargetType");
        targetId = tag.getInt("TargetId");
        latchedTreeSupport = tag.getInt("SyncSupports");
        isFiring = tag.getBoolean("IsFiring");
        lastFireTime = tag.getLong("LastFireTime");
        visualHasEnergy = tag.getBoolean("HasPower");
        if (tag.contains("TargetPosLong"))
            targetPos = BlockPos.of(tag.getLong("TargetPosLong"));
        if (tag.contains("MasterPosLong"))
            masterPos = BlockPos.of(tag.getLong("MasterPosLong"));
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public long getLastFireTime() {
        return lastFireTime;
    }

    public int getTargetType() {
        return targetType;
    }

    public int getTargetId() {
        return targetId;
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public int getSupportCount() {
        return latchedTreeSupport;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
            this.visualHasEnergy = this.energyStorage.getEnergyStored() >= 500;
            if (this.targetType != 0) {
                this.visualTargetType = this.targetType;
                this.visualTargetId = this.targetId;
                this.visualTargetPos = this.targetPos;
            }
            this.visualSupportCount = this.latchedTreeSupport;
            if (this.isFiring) {
                this.visualCountdown = 6;
                if (this.targetType == 1 && this.targetId != -1 && this.level != null) {
                    Entity target = this.level.getEntity(this.targetId);
                    if (target != null)
                        this.visualCachedTargetPos = target.getEyePosition(0.0f);
                } else if (this.targetType == 2 && this.targetPos != null) {
                    this.visualCachedTargetPos = Vec3.atLowerCornerOf(this.targetPos).add(0.5, 2.875, 0.5);
                }
            }
        }
    }
}
