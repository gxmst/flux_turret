package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.util.TurretVisualEffects;
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
    private static final double REMOTE_SUPPORT_MAX_MONSTER_SCAN_RANGE = 32.0;
    private static final int NEIGHBOR_SCAN_RANGE = 12;
    private static final int REMOTE_SUPPORT_NEIGHBOR_SCAN_RANGE = 18;
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
    private int dyeColorIndex = -1;
    private double cachedEffectiveRange = -1;
    private int cachedPotentialSupports = 0;

    // Performance optimization: mark support tree as dirty only when needed
    private boolean supportTreeDirty = true;
    private int supportTreeRecalcCooldown = 0;

    private List<PrismTowerBlockEntity> neighborCache = List.of();

    public PrismTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRISM_TOWER_BE.get(), pos, state, TurretConfig.PRISM_CAPACITY.get(), MAX_RECEIVE);
    }

    @Override
    protected double getTargetRange() {
        if (cachedEffectiveRange < 0) {
            cachedEffectiveRange = getEffectiveScanRange();
        }
        return cachedEffectiveRange;
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
        // Prism's active clip is a steady-state spin (no transient to truncate), so
        // this isn't the grand-cannon snap-back bug. 12 ticks keeps a master tower
        // (fires ~every 20t) reading as "engaged" between shots instead of flickering
        // active<->idle each shot; slave relays refresh it every 2t regardless.
        return 12;
    }

    @Override
    protected int getMinOperatingCost() {
        return TurretConfig.PRISM_SLAVE_FIRE_COST.get();
    }

    @Override
    public boolean canInstallUpgrade(TurretUpgradeType type) {
        return type == TurretUpgradeType.FOCUSED_BEAM
                || type == TurretUpgradeType.REFRACTION_BEAM
                || type == TurretUpgradeType.REMOTE_SUPPORT;
    }

    @Override
    public void installUpgrade(TurretUpgradeType type) {
        super.installUpgrade(type);
        cachedEffectiveRange = -1;
        supportTreeDirty = true;
        supportTreeRecalcCooldown = 0;
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putInt("Depth", currentDepth);
        tag.putInt("TargetType", targetType);
        tag.putInt("SyncSupports", cachedSupportCount);
        tag.putInt("DyeColorIndex", dyeColorIndex);
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
        dyeColorIndex = tag.contains("DyeColorIndex") ? tag.getInt("DyeColorIndex") : -1;
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
            visualCountdown = getFiringVisualCountdown();
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
        // Only recalculate if marked dirty and cooldown expired
        if (supportTreeDirty && supportTreeRecalcCooldown <= 0) {
            cachedPotentialSupports = computePotentialSupportCount();
            supportTreeDirty = false;
            supportTreeRecalcCooldown = 40; // 2 seconds minimum between recalculations
        }
        double maxRange = hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT)
                ? REMOTE_SUPPORT_MAX_MONSTER_SCAN_RANGE
                : MAX_MONSTER_SCAN_RANGE;
        double supportBonus = hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT)
                ? SUPPORT_RANGE_BONUS * 1.35
                : SUPPORT_RANGE_BONUS;
        return Math.min(maxRange, TurretConfig.PRISM_RANGE.get() + cachedPotentialSupports * supportBonus);
    }

    private int getNeighborScanRange() {
        return hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT)
                ? REMOTE_SUPPORT_NEIGHBOR_SCAN_RANGE
                : NEIGHBOR_SCAN_RANGE;
    }

    private void refreshNeighborCache(Level level, BlockPos pos) {
        List<PrismTowerBlockEntity> result = new ArrayList<>();
        int scanRange = getNeighborScanRange();
        int chunkRange = (scanRange >> 4) + 1;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof PrismTowerBlockEntity other && !be.getBlockPos().equals(pos)) {
                        if (withinRange(be.getBlockPos(), pos, scanRange)) {
                            result.add(other);
                        }
                    }
                }
            }
        }
        neighborCache = result;
        // Mark support tree dirty when neighbors change
        supportTreeDirty = true;
    }

    private int computePotentialSupportCount() {
        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int supportCount = 0;

        for (PrismTowerBlockEntity neighbor : neighborCache) {
            BlockPos np = neighbor.getBlockPos();
            if (visited.contains(np)) continue;
            if (neighbor.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) continue;
            if (!withinRange(np, getBlockPos(), getNeighborScanRange())) continue;
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
                if (!withinRange(nnPos, node.pos, currentTE.getNeighborScanRange())) continue;
                visited.add(nnPos);
                queue.add(new SupportNode(nnPos, node.depth + 1));
                supportCount++;
            }
        }

        return supportCount;
    }

    private record SupportNode(BlockPos pos, int depth) {
    }

    @Override
    protected int getFireTargetType() {
        return targetType;
    }

    @Override
    protected BlockPos getFireTargetPos() {
        return targetPos;
    }

    @Override
    public void onClientFire(long gameTime, int firedTargetId, int firedTargetType, BlockPos firedTargetPos) {
        lastFireTime = gameTime;
        visualCountdown = getFiringVisualCountdown();
        visualTargetType = firedTargetType;
        visualTargetId = firedTargetId;
        visualTargetPos = firedTargetPos;
        // Refresh the resolved beam endpoint immediately so the first frame after
        // firing aims at the correct target rather than a stale cached position.
        visualCachedTargetPos = null;
        if (firedTargetType == 1 && firedTargetId != -1 && level != null) {
            Entity target = level.getEntity(firedTargetId);
            if (target != null) {
                visualCachedTargetPos = target.getEyePosition(0.0f);
            }
        } else if (firedTargetType == 2 && firedTargetPos != null) {
            visualCachedTargetPos = Vec3.atLowerCornerOf(firedTargetPos).add(0.5, 3.125, 0.5);
        }
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
        if (parent.masterPos.equals(relayPos)) return false;
        if (parent.getEnergyStorage().getEnergyStored() < TurretConfig.PRISM_SLAVE_FIRE_COST.get()) return false;
        if (!withinRange(parent.getBlockPos(), relayPos, Math.max(parent.getNeighborScanRange(), getNeighborScanRange()))) return false;
        return parent.hasLiveRelayTarget();
    }

    private boolean hasLiveRelayTarget() {
        if (targetType == 1) {
            Entity target = targetId == -1 ? null : level.getEntity(targetId);
            return target != null && target.isAlive();
        }
        if (targetType == 2 && targetPos != null && masterPos != null && level != null) {
            BlockEntity be = level.getBlockEntity(targetPos);
            if (be instanceof PrismTowerBlockEntity parentTower) {
                return parentTower.currentDepth >= 0 && parentTower.currentDepth < this.currentDepth;
            }
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PrismTowerBlockEntity be) {
        if (level.isClientSide) {
            be.baseClientTick(level);
            return;
        }

        be.tickCounter++;

        // Decrement support tree recalc cooldown
        if (be.supportTreeRecalcCooldown > 0) {
            be.supportTreeRecalcCooldown--;
        }

        if (be.tickCounter % NEIGHBOR_CACHE_INTERVAL == 0 || be.neighborCache.isEmpty()) {
            be.refreshNeighborCache(level, pos);
            be.cachedEffectiveRange = be.getEffectiveScanRange();
        }
        if (be.tickCounter % TARGET_CACHE_INTERVAL == 0) {
            be.refreshMonsterCache(level, pos);
        }

        int prevDepth = be.currentDepth;
        int prevTargetType = be.targetType;
        int prevTargetId = be.targetId;
        BlockPos prevTargetPos = be.targetPos;
        BlockPos prevMasterPos = be.masterPos;
        boolean prevFiring = be.isFiring;
        int prevSupportCount = be.cachedSupportCount;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            resetState(be);
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_SLAVE_FIRE_COST.get();
            if (be.currentDepth != prevDepth || be.targetType != prevTargetType || be.targetId != prevTargetId
                    || !java.util.Objects.equals(be.targetPos, prevTargetPos)
                    || !java.util.Objects.equals(be.masterPos, prevMasterPos)
                    || be.isFiring != prevFiring || be.visualHasEnergy != prevHasEnergy) {
                be.markUpdated();
            }
            return;
        }

        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_SLAVE_FIRE_COST.get();
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0)
            be.attackCooldown--;

        if (hasEnoughEnergy) {
            PrismTowerBlockEntity bestParent = be.findBestRelayParent(pos);
            if (bestParent != null && (be.currentDepth != 0 || bestParent.getBlockPos().hashCode() < pos.hashCode())) {
                be.relayFrom(level, bestParent);
            } else {
            Monster closestMonster = be.findClosestMonster(level, pos);

            boolean hasMasterEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.PRISM_MASTER_FIRE_COST.get();
            boolean isMasterPotential = false;
            if (hasMasterEnergy && closestMonster != null) {
                final int masterFireCost = TurretConfig.PRISM_MASTER_FIRE_COST.get();
                double myDistSq = closestMonster.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                // A neighbor can only unseat us as master if it outranks us on
                // distance (or ties and wins the hash tiebreak). Evaluate that cheap
                // ranking test BEFORE the expensive line-of-sight raycast, so we only
                // clip() against neighbors that could actually beat us.
                isMasterPotential = be.neighborCache.stream()
                        .filter(t -> t.getEnergyStorage().getEnergyStored() >= masterFireCost)
                        .filter(t -> {
                            double nDistSq = closestMonster.distanceToSqr(
                                    t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ());
                            if (Math.abs(nDistSq - myDistSq) < 1.0)
                                return t.getBlockPos().hashCode() < pos.hashCode();
                            return nDistSq < myDistSq;
                        })
                        .noneMatch(t -> t.isValidTarget(closestMonster, level, t.getBlockPos()));
            }

            if (isMasterPotential && closestMonster != null) {
                be.currentDepth = 0;
                be.masterPos = pos;
                be.targetType = 1;
                be.targetId = closestMonster.getId();
                be.targetPos = null;

                be.cachedSupportCount = be.cachedPotentialSupports;

                if (be.attackCooldown <= 0) {
                    be.warmupTicks++;
                    if (be.warmupTicks >= WARMUP_TICKS) {
                        int damageSupports = Math.min(be.cachedSupportCount, DAMAGE_SUPPORT_CAP);
                        float damage = TurretConfig.PRISM_DAMAGE.get().floatValue() * (1.0f + damageSupports * SUPPORT_DAMAGE_MULT);
                        if (be.hasUpgrade(TurretUpgradeType.FOCUSED_BEAM)) {
                            damage *= 1.45f;
                        }
                        if (be.getEnergyStorage().consumeEnergy(TurretConfig.PRISM_MASTER_FIRE_COST.get())) {
                            // Reset invulnerability to ensure damage is applied
                            closestMonster.invulnerableTime = 0;
                            closestMonster.hurt(level.damageSources().magic(), damage);
                            if (be.hasUpgrade(TurretUpgradeType.REFRACTION_BEAM)) {
                                be.refractBeam(level, closestMonster, damage * 0.38f);
                            }

                            // Enhanced Red Alert style prism beam
                            Vec3 prismTop = Vec3.atCenterOf(pos).add(0, 2.0, 0);
                            Vec3 targetPos = closestMonster.position().add(0, closestMonster.getBbHeight() * 0.5, 0);
                            TurretVisualEffects.spawnPrismBeam(level, prismTop, targetPos, be.cachedSupportCount);

                            // Sound pitch varies with support count (more supports = higher pitch)
                            float pitchBase = 0.6f + (damageSupports * 0.05f);
                            TurretVisualEffects.playTurretSound(level, pos, ModRegistry.PRISM_SHOOT.get(),
                                0.25f, pitchBase, 0.08f);

                            be.isFiring = true;
                            be.lastFireTime = level.getGameTime();
                            be.attackCooldown = MASTER_COOLDOWN;
                            be.warmupTicks = 0;
                            be.sendFirePacket();
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
                    bestParent = be.findBestRelayParent(pos);
                    if (bestParent != null) {
                        be.relayFrom(level, bestParent);
                    } else {
                        resetState(be);
                    }
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
                || be.cachedSupportCount != prevSupportCount
                || be.visualHasEnergy != prevHasEnergy) {
            be.markUpdated();
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

    private PrismTowerBlockEntity findBestRelayParent(BlockPos pos) {
        return neighborCache.stream()
                .filter(t -> canRelayFrom(pos, t))
                .min(Comparator
                        .comparingInt((PrismTowerBlockEntity t) -> t.currentDepth)
                        .thenComparingDouble(t -> t.getBlockPos().distSqr(pos))
                        .thenComparingInt(t -> t.getBlockPos().hashCode()))
                .orElse(null);
    }

    private void relayFrom(Level level, PrismTowerBlockEntity bestParent) {
        currentDepth = bestParent.currentDepth + 1;
        masterPos = bestParent.masterPos;
        targetType = 2;
        targetPos = bestParent.getBlockPos();
        targetId = -1;
        warmupTicks = 0;

        if (attackCooldown <= 0 && getEnergyStorage().consumeEnergy(TurretConfig.PRISM_SLAVE_FIRE_COST.get())) {
            isFiring = true;
            lastFireTime = level.getGameTime();
            attackCooldown = SLAVE_COOLDOWN;
            sendFirePacket();
        } else {
            isFiring = false;
        }
    }

    private void refractBeam(Level level, Monster primaryTarget, float damage) {
        Vec3 primaryPos = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.5, 0);
        double range = hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT) ? 8.0 : 6.0;
        int maxTargets = hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT) ? 4 : 3;
        List<Monster> refractionTargets = level.getEntitiesOfClass(Monster.class,
                        primaryTarget.getBoundingBox().inflate(range), monster ->
                                monster != primaryTarget && monster.isAlive() && !monster.isRemoved()
                                        && monster.position().distanceTo(primaryPos) <= range
                                        && (!TurretConfig.FRIENDLY_FIRE_PROTECTION.get() || !monster.hasCustomName()))
                .stream()
                .sorted(Comparator.comparingDouble(monster -> monster.position().distanceToSqr(primaryPos)))
                .limit(maxTargets)
                .toList();
        for (Monster monster : refractionTargets) {
            monster.invulnerableTime = 0;
            monster.hurt(level.damageSources().magic(), damage);
            Vec3 refractPos = monster.position().add(0, monster.getBbHeight() * 0.5, 0);
            TurretVisualEffects.spawnPrismBeam(level, primaryPos, refractPos, Math.max(1, cachedSupportCount / 2));
            damage *= 0.82f;
        }
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

    public int getDyeColorIndex() {
        return dyeColorIndex;
    }

    public void setDyeColorIndex(int index) {
        this.dyeColorIndex = index;
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
