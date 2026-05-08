package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class PrismTowerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // --- Constants ---
    private static final int CAPACITY = 100000;
    private static final int MAX_RECEIVE = 1000;
    private static final int MASTER_FIRE_COST = 1000;
    private static final int SLAVE_FIRE_COST = 500;
    private static final double BASE_MONSTER_SCAN_RANGE = 16.5;
    private static final double SUPPORT_RANGE_BONUS = 0.75;
    private static final double MAX_MONSTER_SCAN_RANGE = 24.0;
    private static final int NEIGHBOR_SCAN_RANGE = 12;
    private static final int MAX_DEPTH = 6;
    private static final int WARMUP_TICKS = 10;
    private static final int MASTER_COOLDOWN = 20;
    private static final int SLAVE_COOLDOWN = 2;
    private static final float BASE_DAMAGE = 10.0f;
    private static final float SUPPORT_DAMAGE_MULT = 0.35f;
    private static final int DAMAGE_SUPPORT_CAP = 12;
    private static final int SUPPORT_SCAN_CAP = 100;
    private static final int NEIGHBOR_CACHE_INTERVAL = 20;
    private static final int TARGET_CACHE_INTERVAL = 10;

    // --- Visual Smoothing (Transient, client-side only) ---
    public int visualCountdown = 0;
    public int visualTargetType = 0;
    public int visualTargetId = -1;
    public BlockPos visualTargetPos = null;
    public int visualSupportCount = 0;
    public Vec3 visualCachedTargetPos = null;
    public boolean visualHasEnergy = false;

    // --- Core Logic State ---
    private int currentDepth = -1;
    private int lastDepth = -1;
    private BlockPos masterPos = null;
    private int targetId = -1;
    private BlockPos targetPos = null;
    private int targetType = 0; // 0: None, 1: Entity, 2: BlockPos

    private int attackCooldown = 0;
    private int warmupTicks = 0;
    private boolean isFiring = false;
    private long lastFireTime = 0;

    // --- Cached support count (computed deterministically by master) ---
    private int cachedSupportCount = 0;

    // --- Performance caches (server-side, transient) ---
    private int tickCounter = 0;
    private List<PrismTowerBlockEntity> neighborCache = List.of();
    private List<Monster> monsterCache = List.of();

    // --- Energy Storage ---
    // External-facing: receive-only. Internal logic uses consumeEnergy() directly.
    private static class PrismEnergyStorage extends EnergyStorage {
        public PrismEnergyStorage(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        public void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(energy, this.capacity));
        }

        /**
         * Internal consumption: directly deducts energy bypassing canExtract/maxExtract.
         * Returns true if the full amount was consumed.
         */
        public boolean consumeEnergy(int amount) {
            if (amount <= 0) return true;
            if (this.energy < amount) return false;
            this.energy -= amount;
            return true;
        }
    }

    private final PrismEnergyStorage energyStorage = new PrismEnergyStorage(CAPACITY, MAX_RECEIVE) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate)
                setChanged();
            return received;
        }
    };

    // Non-final so reviveCaps() can rebuild after invalidateCaps()
    private LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);

    public PrismTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRISM_TOWER_BE.get(), pos, state);
    }

    // --- Capability Lifecycle ---

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
    public void reviveCaps() {
        super.reviveCaps();
        energyHandler = LazyOptional.of(() -> energyStorage);
    }

    // --- NBT Persistence ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Depth", currentDepth);
        tag.putInt("TargetType", targetType);
        tag.putInt("TargetId", targetId);
        tag.putInt("SyncSupports", cachedSupportCount);
        tag.putBoolean("IsFiring", isFiring);
        tag.putLong("LastFireTime", lastFireTime);
        tag.putBoolean("HasPower", visualHasEnergy);
        if (targetPos != null)
            tag.putLong("TargetPosLong", targetPos.asLong());
        if (masterPos != null)
            tag.putLong("MasterPosLong", masterPos.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Clear nullable fields first to avoid stale data when tags are missing
        targetPos = null;
        masterPos = null;

        energyStorage.setEnergy(tag.getInt("Energy"));
        currentDepth = tag.getInt("Depth");
        targetType = tag.getInt("TargetType");
        targetId = tag.getInt("TargetId");
        cachedSupportCount = tag.getInt("SyncSupports");
        isFiring = tag.getBoolean("IsFiring");
        lastFireTime = tag.getLong("LastFireTime");
        visualHasEnergy = tag.getBoolean("HasPower");
        if (tag.contains("TargetPosLong"))
            targetPos = BlockPos.of(tag.getLong("TargetPosLong"));
        if (tag.contains("MasterPosLong"))
            masterPos = BlockPos.of(tag.getLong("MasterPosLong"));
    }

    // --- Network Sync ---

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
            this.visualHasEnergy = this.energyStorage.getEnergyStored() >= SLAVE_FIRE_COST;

            // Clear all visual target state first
            this.visualTargetType = 0;
            this.visualTargetId = -1;
            this.visualTargetPos = null;
            this.visualCachedTargetPos = null;
            this.visualCountdown = 0;

            if (this.targetType != 0) {
                this.visualTargetType = this.targetType;
                this.visualTargetId = this.targetId;
                this.visualTargetPos = this.targetPos;
            }
            this.visualSupportCount = this.cachedSupportCount;
            if (this.isFiring && this.targetType != 0) {
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

    // --- Target Predicate ---

    private boolean isValidTarget(Monster monster, Level level, BlockPos selfPos) {
        if (!monster.isAlive()) return false;
        // Line-of-sight check
        Vec3 eyePos = new Vec3(selfPos.getX() + 0.5, selfPos.getY() + 2.5, selfPos.getZ() + 0.5);
        Vec3 targetEye = monster.getEyePosition(0.0f);
        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos, targetEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null));
        // If the ray hits a block before reaching the target, no LOS
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double hitDistSq = hitResult.getLocation().distanceToSqr(eyePos);
            double targetDistSq = targetEye.distanceToSqr(eyePos);
            if (hitDistSq < targetDistSq - 1.0) return false;
        }
        // Team/owner filtering: skip tamed entities or same-team entities
        // (extensible: add whitelist/blacklist here)
        return true;
    }

    // --- Performance: Cache Refresh ---

    private void refreshNeighborCache(Level level, BlockPos pos) {
        List<PrismTowerBlockEntity> result = new ArrayList<>();
        for (BlockPos bp : BlockPos.betweenClosed(pos.offset(-NEIGHBOR_SCAN_RANGE, -NEIGHBOR_SCAN_RANGE, -NEIGHBOR_SCAN_RANGE),
                pos.offset(NEIGHBOR_SCAN_RANGE, NEIGHBOR_SCAN_RANGE, NEIGHBOR_SCAN_RANGE))) {
            if (bp.equals(pos)) continue;
            BlockEntity be = level.getBlockEntity(bp);
            if (be instanceof PrismTowerBlockEntity other) {
                result.add(other);
            }
        }
        neighborCache = result;
    }

    private void refreshMonsterCache(Level level, BlockPos pos) {
        AABB scanArea = new AABB(pos).inflate(getEffectiveScanRange(level, pos));
        monsterCache = level.getEntitiesOfClass(Monster.class, scanArea,
                m -> m.isAlive() && isValidTarget(m, level, pos));
    }

    private double getEffectiveScanRange(Level level, BlockPos pos) {
        int supportCount = computePotentialSupportCount(level, pos);
        return Math.min(MAX_MONSTER_SCAN_RANGE, BASE_MONSTER_SCAN_RANGE + supportCount * SUPPORT_RANGE_BONUS);
    }

    private int computePotentialSupportCount(Level level, BlockPos rootPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int supportCount = 0;

        for (PrismTowerBlockEntity neighbor : neighborCache) {
            BlockPos np = neighbor.getBlockPos();
            if (visited.contains(np)) continue;
            if (neighbor.energyStorage.getEnergyStored() < SLAVE_FIRE_COST) continue;
            if (!withinRange(np, rootPos, NEIGHBOR_SCAN_RANGE)) continue;
            visited.add(np);
            queue.add(new SupportNode(np, 1));
            supportCount++;
        }

        while (!queue.isEmpty() && supportCount < SUPPORT_SCAN_CAP) {
            SupportNode node = queue.poll();
            if (node.depth >= MAX_DEPTH) continue;
            BlockEntity be = level.getBlockEntity(node.pos);
            if (!(be instanceof PrismTowerBlockEntity currentTE)) continue;

            for (PrismTowerBlockEntity nn : currentTE.neighborCache) {
                BlockPos nnPos = nn.getBlockPos();
                if (visited.contains(nnPos)) continue;
                if (nn.energyStorage.getEnergyStored() < SLAVE_FIRE_COST) continue;
                if (!withinRange(nnPos, node.pos, NEIGHBOR_SCAN_RANGE)) continue;
                visited.add(nnPos);
                queue.add(new SupportNode(nnPos, node.depth + 1));
                supportCount++;
            }
        }

        return supportCount;
    }

    // --- Deterministic Support Tree Computation ---
    // Master walks the neighbor graph via BFS, only counting powered relays
    // that already self-registered in the tree (depth >= 0).

    private int computeSupportTree(Level level, BlockPos masterBlockPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int supportCount = 0;

        // Seed: master's immediate neighbors that are active relays in THIS tree
        for (PrismTowerBlockEntity neighbor : neighborCache) {
            if (neighbor == this) continue;
            BlockPos np = neighbor.getBlockPos();
            if (visited.contains(np)) continue;
            if (neighbor.currentDepth < 0) continue;
            if (neighbor.energyStorage.getEnergyStored() < SLAVE_FIRE_COST) continue;
            // Must belong to this master's tree
            if (neighbor.masterPos == null || !neighbor.masterPos.equals(masterBlockPos)) continue;
            if (!withinRange(np, masterBlockPos, NEIGHBOR_SCAN_RANGE)) continue;
            visited.add(np);
            queue.add(new SupportNode(np, 1));
            supportCount++;
        }

        // BFS outward: each node must be within range of its parent (not the master),
        // belong to the same master, and not exceed max depth.
        while (!queue.isEmpty() && supportCount < SUPPORT_SCAN_CAP) {
            SupportNode node = queue.poll();
            if (node.depth >= MAX_DEPTH) continue;
            BlockEntity be = level.getBlockEntity(node.pos);
            if (!(be instanceof PrismTowerBlockEntity currentTE)) continue;

            for (PrismTowerBlockEntity nn : currentTE.neighborCache) {
                BlockPos nnPos = nn.getBlockPos();
                if (visited.contains(nnPos)) continue;
                if (nn.currentDepth < 0) continue;
                if (nn.energyStorage.getEnergyStored() < SLAVE_FIRE_COST) continue;
                // Must belong to the same master's tree
                if (nn.masterPos == null || !nn.masterPos.equals(masterBlockPos)) continue;
                // Must be within range of its parent (the current node), not the master
                if (!withinRange(nnPos, node.pos, NEIGHBOR_SCAN_RANGE)) continue;
                visited.add(nnPos);
                queue.add(new SupportNode(nnPos, node.depth + 1));
                supportCount++;
            }
        }
        return supportCount;
    }

    private record SupportNode(BlockPos pos, int depth) {}

    private static boolean withinRange(BlockPos a, BlockPos b, int range) {
        return Math.abs(a.getX() - b.getX()) <= range
                && Math.abs(a.getY() - b.getY()) <= range
                && Math.abs(a.getZ() - b.getZ()) <= range;
    }

    private boolean canRelayFrom(Level level, BlockPos relayPos, PrismTowerBlockEntity parent) {
        if (parent == this) return false;
        if (parent.currentDepth < 0 || parent.currentDepth >= MAX_DEPTH) return false;
        if (parent.masterPos == null) return false;
        if (parent.energyStorage.getEnergyStored() < SLAVE_FIRE_COST) return false;
        if (!withinRange(parent.getBlockPos(), relayPos, NEIGHBOR_SCAN_RANGE)) return false;
        return parent.hasLiveRelayTarget(level);
    }

    private boolean hasLiveRelayTarget(Level level) {
        if (targetType == 1) {
            Entity target = targetId == -1 ? null : level.getEntity(targetId);
            return target != null && target.isAlive();
        }
        return targetType == 2 && targetPos != null && masterPos != null;
    }

    // --- Main Tick ---

    public static void tick(Level level, BlockPos pos, BlockState state, PrismTowerBlockEntity be) {
        if (level.isClientSide) {
            if (be.visualCountdown > 0)
                be.visualCountdown--;
            return;
        }

        // --- Periodic cache refresh ---
        be.tickCounter++;
        if (be.tickCounter % NEIGHBOR_CACHE_INTERVAL == 0 || be.neighborCache.isEmpty()) {
            be.refreshNeighborCache(level, pos);
        }
        if (be.tickCounter % TARGET_CACHE_INTERVAL == 0 || be.monsterCache.isEmpty()) {
            be.refreshMonsterCache(level, pos);
        }

        // --- Latch previous state for sync comparison ---
        be.lastDepth = be.currentDepth;
        int prevDepth = be.currentDepth;
        int prevTargetType = be.targetType;
        int prevTargetId = be.targetId;
        BlockPos prevTargetPos = be.targetPos;
        BlockPos prevMasterPos = be.masterPos;
        boolean prevFiring = be.isFiring;
        long prevFireTime = be.lastFireTime;
        int prevSupportCount = be.cachedSupportCount;
        boolean prevHasEnergy = be.visualHasEnergy;

        // Minimum operating threshold: enough for one slave relay
        boolean hasEnoughEnergy = be.energyStorage.getEnergyStored() >= SLAVE_FIRE_COST;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0)
            be.attackCooldown--;

        if (hasEnoughEnergy) {
            // Find closest valid monster from cache
            Monster closestMonster = be.monsterCache.stream()
                    .min(Comparator.comparingDouble(m -> m.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                    .orElse(null);

            // Master election: only towers with enough energy for a master fire
            boolean hasMasterEnergy = be.energyStorage.getEnergyStored() >= MASTER_FIRE_COST;
            boolean isMasterPotential = false;
            if (hasMasterEnergy && closestMonster != null) {
                double myDistSq = closestMonster.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                isMasterPotential = be.neighborCache.stream()
                        .filter(t -> t.energyStorage.getEnergyStored() >= MASTER_FIRE_COST)
                        .filter(t -> t.isValidTarget(closestMonster, level, t.getBlockPos()))
                        .noneMatch(t -> {
                    double nDistSq = closestMonster.distanceToSqr(
                            t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ());
                    if (Math.abs(nDistSq - myDistSq) < 1.0)
                        return t.getBlockPos().hashCode() < pos.hashCode();
                    return nDistSq < myDistSq;
                });
            }

            if (isMasterPotential && closestMonster != null) {
                // === MASTER LOGIC ===
                be.currentDepth = 0;
                be.masterPos = pos;
                be.targetType = 1;
                be.targetId = closestMonster.getId();
                be.targetPos = null;

                // Deterministic support tree computation
                be.cachedSupportCount = be.computeSupportTree(level, pos);

                if (be.attackCooldown <= 0) {
                    be.warmupTicks++;
                    if (be.warmupTicks >= WARMUP_TICKS) {
                        int damageSupports = Math.min(be.cachedSupportCount, DAMAGE_SUPPORT_CAP);
                        float damage = BASE_DAMAGE * (1.0f + damageSupports * SUPPORT_DAMAGE_MULT);
                        if (be.energyStorage.consumeEnergy(MASTER_FIRE_COST)) {
                            closestMonster.hurt(level.damageSources().magic(), damage);
                            level.playSound(null, pos, SoundEvents.GUARDIAN_ATTACK, SoundSource.BLOCKS, 1.0f,
                                    1.2f + be.cachedSupportCount * 0.05f);
                            be.isFiring = true;
                            be.lastFireTime = level.getGameTime();
                            be.attackCooldown = MASTER_COOLDOWN;
                            be.warmupTicks = 0;
                        }
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
                // === SLAVE LOGIC ===
                // Must have enough energy to participate as a relay
                if (be.energyStorage.getEnergyStored() < SLAVE_FIRE_COST) {
                    resetState(be);
                } else {
                    PrismTowerBlockEntity bestParent = be.neighborCache.stream()
                            .filter(t -> be.canRelayFrom(level, pos, t))
                            .min(Comparator
                                    .comparingInt((PrismTowerBlockEntity t) -> t.currentDepth)
                                    .thenComparingDouble(t -> t.getBlockPos().distSqr(pos))
                                    .thenComparingInt(t -> t.getBlockPos().hashCode()))
                            .orElse(null);

                    if (bestParent != null) {
                        be.currentDepth = bestParent.currentDepth + 1;
                        be.lastDepth = be.currentDepth;
                        be.masterPos = bestParent.masterPos;
                        be.targetType = 2;
                        be.targetPos = bestParent.getBlockPos();
                        be.targetId = -1;
                        be.warmupTicks = 0;

                        if (be.attackCooldown <= 0) {
                            if (be.energyStorage.consumeEnergy(SLAVE_FIRE_COST)) {
                                be.isFiring = true;
                                be.lastFireTime = level.getGameTime();
                                be.attackCooldown = SLAVE_COOLDOWN;
                            }
                        }
                    } else {
                        resetState(be);
                    }
                }
            }
        } else {
            resetState(be);
        }

        // --- Sync on change ---
        if (be.currentDepth != prevDepth
                || be.targetType != prevTargetType
                || be.targetId != prevTargetId
                || !java.util.Objects.equals(be.targetPos, prevTargetPos)
                || !java.util.Objects.equals(be.masterPos, prevMasterPos)
                || be.isFiring != prevFiring
                || be.lastFireTime != prevFireTime
                || be.cachedSupportCount != prevSupportCount
                || be.visualHasEnergy != prevHasEnergy) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static void resetState(PrismTowerBlockEntity be) {
        be.currentDepth = -1;
        be.lastDepth = -1;
        be.masterPos = null;
        be.targetType = 0;
        be.targetId = -1;
        be.targetPos = null;
        be.isFiring = false;
        be.warmupTicks = 0;
        be.cachedSupportCount = 0;
    }

    // --- Public Getters ---

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
        return cachedSupportCount;
    }

    public int getDepth() {
        return currentDepth;
    }

    public boolean isVisuallyPowered() {
        return visualHasEnergy || energyStorage.getEnergyStored() >= SLAVE_FIRE_COST;
    }

    // --- GeckoLib ---

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
}
