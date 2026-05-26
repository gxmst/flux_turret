package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class PrismTowerBlockEntity extends TurretBlockEntityBase {
    private static final int MAX_RECEIVE = 1000;
    private static final double SUPPORT_RANGE_BONUS = 0.75;
    private static final double MAX_MONSTER_SCAN_RANGE = 24.0;
    private static final int NEIGHBOR_SCAN_RANGE = 12;
    private static final int MAX_DEPTH = 6;
    private static final int WARMUP_TICKS = 10;
    private static final int MASTER_COOLDOWN = 20;
    private static final int SLAVE_COOLDOWN = 2;
    private static final float SUPPORT_DAMAGE_MULT = 0.35f;
    private static final int DAMAGE_SUPPORT_CAP = 12;
    private static final int SUPPORT_SCAN_CAP = 100;
    private static final int NEIGHBOR_CACHE_INTERVAL = 20;
    private static final int TARGET_CACHE_INTERVAL = 10;

    public int visualTargetType = 0;
    public BlockPos visualTargetPos = null;
    public int visualSupportCount = 0;

    private int currentDepth = -1;
    private BlockPos masterPos = null;
    private int targetType = 0;
    private BlockPos targetPos = null;
    private int warmupTicks = 0;
    private int cachedSupportCount = 0;

    private List<PrismTowerBlockEntity> neighborCache = List.of();

    public PrismTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRISM_TOWER_BE.get(), pos, state, TurretConfig.PRISM_CAPACITY.get(), MAX_RECEIVE);
    }

    @Override
    protected double getTargetRange() {
        return getEffectiveScanRange();
    }

    @Override
    protected double getEyeHeight() {
        return 3.125;
    }

    @Override
    protected int getTargetCacheInterval() {
        return TARGET_CACHE_INTERVAL;
    }

    @Override
    protected int getFiringVisualCountdown() {
        return 6;
    }

    @Override
    protected int getMinOperatingCost() {
        return TurretConfig.PRISM_SLAVE_FIRE_COST.get();
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putInt("Depth", currentDepth);
        tag.putInt("TargetType", targetType);
        tag.putInt("SyncSupports", cachedSupportCount);
        if (targetPos != null)
            tag.putLong("TargetPosLong", targetPos.asLong());
        if (masterPos != null)
            tag.putLong("MasterPosLong", masterPos.asLong());
    }

    @Override
    protected void loadAdditionalTurret(CompoundTag tag) {
        targetPos = null;
        masterPos = null;

        currentDepth = tag.getInt("Depth");
        targetType = tag.getInt("TargetType");
        cachedSupportCount = tag.getInt("SyncSupports");
        if (tag.contains("TargetPosLong"))
            targetPos = BlockPos.of(tag.getLong("TargetPosLong"));
        if (tag.contains("MasterPosLong"))
            masterPos = BlockPos.of(tag.getLong("MasterPosLong"));
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag == null) return;

        load(tag);
        visualHasEnergy = getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_SLAVE_FIRE_COST.get();

        visualTargetType = 0;
        visualTargetId = -1;
        visualTargetPos = null;
        visualCachedTargetPos = null;
        visualCountdown = 0;

        if (targetType != 0) {
            visualTargetType = targetType;
            visualTargetId = targetId;
            visualTargetPos = targetPos;
        }
        visualSupportCount = cachedSupportCount;
        if (isFiring && targetType != 0) {
            visualCountdown = 6;
            if (targetType == 1 && targetId != -1 && level != null) {
                Entity target = level.getEntity(targetId);
                if (target != null)
                    visualCachedTargetPos = target.getEyePosition(0.0f);
            } else if (targetType == 2 && targetPos != null) {
                visualCachedTargetPos = Vec3.atLowerCornerOf(targetPos).add(0.5, 3.125, 0.5);
            }
        }
    }

    private double getEffectiveScanRange() {
        int supportCount = computePotentialSupportCount();
        return Math.min(MAX_MONSTER_SCAN_RANGE, TurretConfig.PRISM_RANGE.get() + supportCount * SUPPORT_RANGE_BONUS);
    }

    private void refreshNeighborCache(Level level, BlockPos pos) {
        List<PrismTowerBlockEntity> result = new ArrayList<>();
        int chunkRange = (NEIGHBOR_SCAN_RANGE >> 4) + 1;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof PrismTowerBlockEntity other && !be.getBlockPos().equals(pos)) {
                        if (withinRange(be.getBlockPos(), pos, NEIGHBOR_SCAN_RANGE)) {
                            result.add(other);
                        }
                    }
                }
            }
        }
        neighborCache = result;
    }

    private int computePotentialSupportCount() {
        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int supportCount = 0;

        for (PrismTowerBlockEntity neighbor : neighborCache) {
            BlockPos np = neighbor.getBlockPos();
            if (visited.contains(np)) continue;
            if (neighbor.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) continue;
            if (!withinRange(np, getBlockPos(), NEIGHBOR_SCAN_RANGE)) continue;
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
                if (nn.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) continue;
                if (!withinRange(nnPos, node.pos, NEIGHBOR_SCAN_RANGE)) continue;
                visited.add(nnPos);
                queue.add(new SupportNode(nnPos, node.depth + 1));
                supportCount++;
            }
        }

        return supportCount;
    }

    private int computeSupportTree(BlockPos masterBlockPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int supportCount = 0;

        for (PrismTowerBlockEntity neighbor : neighborCache) {
            if (neighbor == this) continue;
            BlockPos np = neighbor.getBlockPos();
            if (visited.contains(np)) continue;
            if (neighbor.currentDepth < 0) continue;
            if (neighbor.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) continue;
            if (neighbor.masterPos == null || !neighbor.masterPos.equals(masterBlockPos)) continue;
            if (!withinRange(np, masterBlockPos, NEIGHBOR_SCAN_RANGE)) continue;
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
                if (nn.currentDepth < 0) continue;
                if (nn.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) continue;
                if (nn.masterPos == null || !nn.masterPos.equals(masterBlockPos)) continue;
                if (!withinRange(nnPos, node.pos, NEIGHBOR_SCAN_RANGE)) continue;
                visited.add(nnPos);
                queue.add(new SupportNode(nnPos, node.depth + 1));
                supportCount++;
            }
        }
        return supportCount;
    }

    private record SupportNode(BlockPos pos, int depth) {
    }

    private static boolean withinRange(BlockPos a, BlockPos b, int range) {
        return Math.abs(a.getX() - b.getX()) <= range
                && Math.abs(a.getY() - b.getY()) <= range
                && Math.abs(a.getZ() - b.getZ()) <= range;
    }

    private boolean canRelayFrom(BlockPos relayPos, PrismTowerBlockEntity parent) {
        if (parent == this) return false;
        if (parent.currentDepth < 0 || parent.currentDepth >= MAX_DEPTH) return false;
        if (parent.masterPos == null) return false;
        if (parent.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) return false;
        if (!withinRange(parent.getBlockPos(), relayPos, NEIGHBOR_SCAN_RANGE)) return false;
        return parent.hasLiveRelayTarget();
    }

    private boolean hasLiveRelayTarget() {
        if (targetType == 1) {
            Entity target = targetId == -1 ? null : level.getEntity(targetId);
            return target != null && target.isAlive();
        }
        return targetType == 2 && targetPos != null && masterPos != null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PrismTowerBlockEntity be) {
        if (level.isClientSide) {
            be.baseClientTick(level);
            return;
        }

        be.tickCounter++;
        if (be.tickCounter % NEIGHBOR_CACHE_INTERVAL == 0 || be.neighborCache.isEmpty()) {
            be.refreshNeighborCache(level, pos);
        }
        if (be.tickCounter % TARGET_CACHE_INTERVAL == 0 || be.monsterCache.isEmpty()) {
            be.refreshMonsterCache(level, pos);
        }

        int prevDepth = be.currentDepth;
        int prevTargetType = be.targetType;
        int prevTargetId = be.targetId;
        BlockPos prevTargetPos = be.targetPos;
        BlockPos prevMasterPos = be.masterPos;
        boolean prevFiring = be.isFiring;
        long prevFireTime = be.lastFireTime;
        int prevSupportCount = be.cachedSupportCount;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            resetState(be);
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_SLAVE_FIRE_COST.get();
            if (be.currentDepth != prevDepth || be.targetType != prevTargetType || be.targetId != prevTargetId
                    || !java.util.Objects.equals(be.targetPos, prevTargetPos)
                    || !java.util.Objects.equals(be.masterPos, prevMasterPos)
                    || be.isFiring != prevFiring || be.visualHasEnergy != prevHasEnergy) {
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }

        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_SLAVE_FIRE_COST.get();
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0)
            be.attackCooldown--;

        if (hasEnoughEnergy) {
            Monster closestMonster = be.findClosestMonster(level, pos);

            boolean hasMasterEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_MASTER_FIRE_COST.get();
            boolean isMasterPotential = false;
            if (hasMasterEnergy && closestMonster != null) {
                double myDistSq = closestMonster.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                isMasterPotential = be.neighborCache.stream()
                        .filter(t -> t.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_MASTER_FIRE_COST.get())
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
                be.currentDepth = 0;
                be.masterPos = pos;
                be.targetType = 1;
                be.targetId = closestMonster.getId();
                be.targetPos = null;

                be.cachedSupportCount = be.computeSupportTree(pos);

                if (be.attackCooldown <= 0) {
                    be.warmupTicks++;
                    if (be.warmupTicks >= WARMUP_TICKS) {
                        int damageSupports = Math.min(be.cachedSupportCount, DAMAGE_SUPPORT_CAP);
                        float damage = TurretConfig.PRISM_DAMAGE.get().floatValue() * (1.0f + damageSupports * SUPPORT_DAMAGE_MULT);
                        if (be.getEnergyStorage().consumeEnergy(TurretConfig.PRISM_MASTER_FIRE_COST.get())) {
                            closestMonster.invulnerableTime = 0;
                            closestMonster.hurt(level.damageSources().magic(), damage);
                            level.playSound(null, pos, ModRegistry.PRISM_SHOOT.get(), SoundSource.BLOCKS, 0.25f, 0.6f + level.random.nextFloat() * 0.08f);
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
                if (be.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) {
                    resetState(be);
                } else {
                    PrismTowerBlockEntity bestParent = be.neighborCache.stream()
                            .filter(t -> be.canRelayFrom(pos, t))
                            .min(Comparator
                                    .comparingInt((PrismTowerBlockEntity t) -> t.currentDepth)
                                    .thenComparingDouble(t -> t.getBlockPos().distSqr(pos))
                                    .thenComparingInt(t -> t.getBlockPos().hashCode()))
                            .orElse(null);

                    if (bestParent != null) {
                        be.currentDepth = bestParent.currentDepth + 1;
                        be.masterPos = bestParent.masterPos;
                        be.targetType = 2;
                        be.targetPos = bestParent.getBlockPos();
                        be.targetId = -1;
                        be.warmupTicks = 0;

                        if (be.attackCooldown <= 0) {
                            if (be.getEnergyStorage().consumeEnergy(TurretConfig.PRISM_SLAVE_FIRE_COST.get())) {
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
        be.masterPos = null;
        be.targetType = 0;
        be.targetId = -1;
        be.targetPos = null;
        be.isFiring = false;
        be.warmupTicks = 0;
        be.cachedSupportCount = 0;
    }

    public int getTargetType() {
        return targetType;
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

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 0, state -> {
            if (this.isVisuallyPowered()) {
                if (this.visualCountdown > 0) {
                    return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.prism_tower.active"));
                }
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.prism_tower.idle"));
            }
            return software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(25, 15, 25);
    }
}
