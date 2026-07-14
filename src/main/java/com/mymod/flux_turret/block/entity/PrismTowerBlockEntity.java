package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.util.TurretPerformanceTracker;
import com.mymod.flux_turret.util.TurretVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

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
    private static final int RELAY_PULSE_INTERVAL = MASTER_COOLDOWN + WARMUP_TICKS;
    private static final int FIRING_VISUAL_TICKS = RELAY_PULSE_INTERVAL + 2;
    private static final float SUPPORT_DAMAGE_MULT = 0.35f;
    private static final int DAMAGE_SUPPORT_CAP = 12;
    private static final int SUPPORT_SCAN_CAP = 100;
    private static final int NEIGHBOR_CACHE_INTERVAL = 20;
    private static final int POTENTIAL_SUPPORT_SCAN_INTERVAL = 100;
    private static final int TARGET_CACHE_INTERVAL = 10;

    // Server-thread-only index of loaded towers. Each tower lives in exactly one
    // chunk bucket; local spherical queries replace every tower walking the same
    // 9-25 chunks and make Remote Support links discoverable from both ends.
    private static final Map<Level, Map<Long, Set<PrismTowerBlockEntity>>> LOADED_TOWERS =
            new IdentityHashMap<>();

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
    private long supportReservedUntil = Long.MIN_VALUE;
    private BlockPos supportReservedMasterPos = null;
    private int lastShotTargetType = 0;
    private int lastShotTargetId = -1;
    private BlockPos lastShotTargetPos = null;
    private Vec3 lastShotImpactPos = null;
    private int lastShotSupportCount = 0;

    // Performance optimization: mark support tree as dirty only when needed
    private boolean supportTreeDirty = true;

    private List<PrismTowerBlockEntity> neighborCache = List.of();
    private Level indexedLevel = null;
    private long indexedChunkKey = Long.MIN_VALUE;

    public PrismTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRISM_TOWER_BE.get(), pos, state, TurretConfig.PRISM_CAPACITY.get(), MAX_RECEIVE);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerLoadedTower();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterLoadedTower();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterLoadedTower();
        super.setRemoved();
    }

    /** Called by level-unload handling so no static tower references outlive a dimension. */
    public static void clearTowersForLevel(Level level) {
        Map<Long, Set<PrismTowerBlockEntity>> removed = LOADED_TOWERS.remove(level);
        if (removed == null) return;

        for (Set<PrismTowerBlockEntity> bucket : removed.values()) {
            for (PrismTowerBlockEntity tower : bucket) {
                if (tower.indexedLevel == level) {
                    tower.indexedLevel = null;
                    tower.indexedChunkKey = Long.MIN_VALUE;
                }
            }
        }
    }

    /** Called by server-stop handling as a final guard against cross-world retention. */
    public static void clearLoadedTowers() {
        for (Map.Entry<Level, Map<Long, Set<PrismTowerBlockEntity>>> levelEntry : LOADED_TOWERS.entrySet()) {
            Level level = levelEntry.getKey();
            for (Set<PrismTowerBlockEntity> bucket : levelEntry.getValue().values()) {
                for (PrismTowerBlockEntity tower : bucket) {
                    if (tower.indexedLevel == level) {
                        tower.indexedLevel = null;
                        tower.indexedChunkKey = Long.MIN_VALUE;
                    }
                }
            }
        }
        LOADED_TOWERS.clear();
    }

    private void registerLoadedTower() {
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide || isRemoved()) return;

        long chunkKey = ChunkPos.asLong(getBlockPos());
        if (indexedLevel == currentLevel && indexedChunkKey == chunkKey) {
            Map<Long, Set<PrismTowerBlockEntity>> buckets = LOADED_TOWERS.get(currentLevel);
            Set<PrismTowerBlockEntity> bucket = buckets == null ? null : buckets.get(chunkKey);
            if (bucket != null && bucket.contains(this)) return;
        }

        unregisterLoadedTower();
        LOADED_TOWERS
                .computeIfAbsent(currentLevel, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>())
                .add(this);
        indexedLevel = currentLevel;
        indexedChunkKey = chunkKey;
    }

    private void unregisterLoadedTower() {
        if (indexedLevel == null) return;

        Map<Long, Set<PrismTowerBlockEntity>> buckets = LOADED_TOWERS.get(indexedLevel);
        if (buckets != null) {
            Set<PrismTowerBlockEntity> bucket = buckets.get(indexedChunkKey);
            if (bucket != null) {
                bucket.remove(this);
                if (bucket.isEmpty()) buckets.remove(indexedChunkKey);
            }
            if (buckets.isEmpty()) LOADED_TOWERS.remove(indexedLevel);
        }
        indexedLevel = null;
        indexedChunkKey = Long.MIN_VALUE;
    }

    private static List<PrismTowerBlockEntity> findIndexedTowers(Level level, BlockPos center, int radius) {
        Map<Long, Set<PrismTowerBlockEntity>> buckets = LOADED_TOWERS.get(level);
        if (buckets == null || buckets.isEmpty()) return List.of();

        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        List<PrismTowerBlockEntity> result = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                Set<PrismTowerBlockEntity> bucket = buckets.get(chunkKey);
                if (bucket == null) continue;

                Iterator<PrismTowerBlockEntity> iterator = bucket.iterator();
                while (iterator.hasNext()) {
                    PrismTowerBlockEntity tower = iterator.next();
                    if (tower == null || tower.isRemoved() || tower.getLevel() != level) {
                        iterator.remove();
                        continue;
                    }
                    if (!tower.getBlockPos().equals(center)
                            && isWithinSphericalRange(tower.getBlockPos(), center, radius)) {
                        result.add(tower);
                    }
                }
                if (bucket.isEmpty()) buckets.remove(chunkKey);
            }
        }

        result.sort(Comparator
                .comparingLong((PrismTowerBlockEntity tower) -> squaredDistance(tower.getBlockPos(), center))
                .thenComparing((a, b) -> comparePositions(a.getBlockPos(), b.getBlockPos())));
        return result;
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
        // Keep the beam visible between the much less frequent relay pulses. Relays
        // now pulse at the same cadence as a master's cooldown + warmup. Two grace
        // ticks cover client/server tick ordering without increasing packet rate.
        return FIRING_VISUAL_TICKS;
    }

    @Override
    protected int getMinOperatingCost() {
        return Rules.getOperatingEnergyThreshold(
                TurretConfig.PRISM_MASTER_FIRE_COST.get(),
                TurretConfig.PRISM_SLAVE_FIRE_COST.get());
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
    }

    @Override
    protected TargetingMode getAutomaticTargetingMode() {
        return TargetingMode.HIGHEST_HEALTH;
    }

    @Override
    protected boolean isWarmingUpForDiagnostics() {
        return warmupTicks > 0;
    }

    @Override
    protected boolean hasTargetForDiagnostics() {
        return targetId != -1 || targetPos != null || currentDepth >= 0;
    }

    @Override
    protected void onUpgradeLoadoutChanged() {
        cachedEffectiveRange = -1;
        supportTreeDirty = true;
    }

    @Override
    public List<TurretUpgradeType> removeAllUpgrades() {
        List<TurretUpgradeType> removed = super.removeAllUpgrades();
        if (!removed.isEmpty()) {
            cachedEffectiveRange = -1;
            supportTreeDirty = true;
        }
        return removed;
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putInt("Depth", currentDepth);
        tag.putInt("TargetType", targetType);
        tag.putInt("SyncSupports", cachedSupportCount);
        tag.putInt("DyeColorIndex", dyeColorIndex);
        if (supportReservedMasterPos != null) {
            tag.putLong("SupportReservedUntil", supportReservedUntil);
            tag.putLong("SupportReservedMaster", supportReservedMasterPos.asLong());
        }
        if (lastShotTargetType != 0 && lastShotImpactPos != null) {
            tag.putInt("LastShotTargetType", lastShotTargetType);
            tag.putInt("LastShotTargetId", lastShotTargetId);
            tag.putInt("LastShotSupports", lastShotSupportCount);
            tag.putDouble("LastShotImpactX", lastShotImpactPos.x);
            tag.putDouble("LastShotImpactY", lastShotImpactPos.y);
            tag.putDouble("LastShotImpactZ", lastShotImpactPos.z);
            if (lastShotTargetPos != null) {
                tag.putLong("LastShotTargetPos", lastShotTargetPos.asLong());
            }
        }
        if (targetPos != null)
            tag.putLong("TargetPosLong", targetPos.asLong());
        if (masterPos != null)
            tag.putLong("MasterPosLong", masterPos.asLong());
    }

    @Override
    protected void loadAdditionalTurret(CompoundTag tag) {
        targetPos = null;
        masterPos = null;
        supportReservedMasterPos = null;
        supportReservedUntil = Long.MIN_VALUE;
        clearLastShotSnapshot();

        currentDepth = tag.getInt("Depth");
        targetType = tag.getInt("TargetType");
        cachedSupportCount = tag.getInt("SyncSupports");
        dyeColorIndex = tag.contains("DyeColorIndex") ? tag.getInt("DyeColorIndex") : -1;
        if (tag.contains("SupportReservedMaster")) {
            supportReservedMasterPos = BlockPos.of(tag.getLong("SupportReservedMaster"));
            supportReservedUntil = tag.getLong("SupportReservedUntil");
        }
        if (tag.contains("LastShotTargetType") && tag.contains("LastShotImpactX")) {
            lastShotTargetType = tag.getInt("LastShotTargetType");
            lastShotTargetId = tag.getInt("LastShotTargetId");
            lastShotSupportCount = Math.max(0, tag.getInt("LastShotSupports"));
            lastShotImpactPos = new Vec3(
                    tag.getDouble("LastShotImpactX"),
                    tag.getDouble("LastShotImpactY"),
                    tag.getDouble("LastShotImpactZ"));
            if (tag.contains("LastShotTargetPos")) {
                lastShotTargetPos = BlockPos.of(tag.getLong("LastShotTargetPos"));
            }
        }
        if (tag.contains("TargetPosLong"))
            targetPos = BlockPos.of(tag.getLong("TargetPosLong"));
        if (tag.contains("MasterPosLong"))
            masterPos = BlockPos.of(tag.getLong("MasterPosLong"));
    }

    @Override
    protected void updateClientVisualStateAfterLoad(boolean preserveFireVisual) {
        visualHasEnergy = getEnergyStorage().getEnergyStored() >= getMinOperatingCost();
        if (preserveFireVisual) {
            return;
        }
        visualTargetType = 0;
        visualTargetId = -1;
        visualTargetPos = null;
        visualCachedTargetPos = null;
        visualSupportCount = 0;
        visualCountdown = 0;

        if (lastShotTargetType != 0 && lastShotImpactPos != null) {
            visualCountdown = getRemainingFireVisualTicks(lastFireTime);
        }
        if (visualCountdown > 0) {
            visualTargetType = lastShotTargetType;
            visualTargetId = lastShotTargetId;
            visualTargetPos = lastShotTargetPos;
            visualCachedTargetPos = lastShotImpactPos;
            visualSupportCount = lastShotSupportCount;
            if (lastShotTargetType == 1 && lastShotTargetId != -1 && level != null) {
                Entity target = level.getEntity(lastShotTargetId);
                if (target != null)
                    visualCachedTargetPos = target.getEyePosition(0.0f);
            }
        }
    }

    @Override
    protected void savePortableDataAdditional(CompoundTag tag) {
        tag.putInt("DyeColorIndex", dyeColorIndex);
    }

    private void clearClientVisualTarget() {
        visualCountdown = 0;
        visualTargetType = 0;
        visualTargetId = -1;
        visualTargetPos = null;
        visualCachedTargetPos = null;
        visualSupportCount = 0;
    }

    private double getEffectiveScanRange() {
        // Topology changes bypass the periodic cooldown; energy and reservation
        // changes are picked up by the slower periodic refresh in tick().
        if (supportTreeDirty) {
            cachedPotentialSupports = computePotentialSupportCount();
            supportTreeDirty = false;
        }
        return Rules.calculateEffectiveScanRange(
                TurretConfig.PRISM_RANGE.get(),
                cachedPotentialSupports,
                hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT));
    }

    private int getNeighborScanRange() {
        return hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT)
                ? REMOTE_SUPPORT_NEIGHBOR_SCAN_RANGE
                : NEIGHBOR_SCAN_RANGE;
    }

    private boolean refreshNeighborCache(Level level, BlockPos pos) {
        level.getProfiler().push("flux_turret:prism_neighbor_cache");
        try {
            registerLoadedTower();
            List<PrismTowerBlockEntity> refreshedNeighbors = findLinkedTowers(this);
            if (refreshedNeighbors.equals(neighborCache)) return false;

            neighborCache = refreshedNeighbors;
            supportTreeDirty = true;
            return true;
        } finally {
            level.getProfiler().pop();
        }
    }

    private int computePotentialSupportCount() {
        if (level == null || level.isClientSide) return 0;

        int minEnergy = TurretConfig.PRISM_SLAVE_FIRE_COST.get();
        long gameTime = level.getGameTime();
        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int supportCount = 0;
        int potentialSupportLimit = Rules.getPotentialSupportScanLimit(
                TurretConfig.PRISM_RANGE.get(),
                hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT));
        if (potentialSupportLimit <= 0) return 0;

        int visitedNodes = 0;
        level.getProfiler().push("flux_turret:prism_potential_bfs");
        try {
            visited.add(getBlockPos());
            queue.add(new SupportNode(this, 0));

            while (!queue.isEmpty() && supportCount < potentialSupportLimit) {
                SupportNode node = queue.poll();
                visitedNodes++;
                if (node.depth >= MAX_DEPTH) continue;

                for (PrismTowerBlockEntity candidate : findLinkedTowers(node.tower)) {
                    if (supportCount >= potentialSupportLimit) break;
                    BlockPos candidatePos = candidate.getBlockPos();
                    if (visited.contains(candidatePos)) continue;
                    if (!isPotentialSupportTower(candidate, minEnergy, gameTime)) continue;

                    visited.add(candidatePos);
                    queue.add(new SupportNode(candidate, node.depth + 1));
                    supportCount++;
                }
            }
        } finally {
            level.getProfiler().pop();
            TurretPerformanceTracker.recordPrismBfs(level, visitedNodes);
        }

        return supportCount;
    }

    private boolean isOperationalTower(PrismTowerBlockEntity tower, int minEnergy) {
        Level currentLevel = this.level;
        return currentLevel != null
                && tower != null
                && !tower.isRemoved()
                && tower.getLevel() == currentLevel
                && currentLevel.shouldTickBlocksAt(tower.getBlockPos())
                && tower.getEnergyStorage().getEnergyStored() >= minEnergy
                && !tower.isRedstoneBlocked(currentLevel, tower.getBlockPos());
    }

    private boolean isPotentialSupportTower(PrismTowerBlockEntity tower, int minEnergy, long gameTime) {
        if (!isOperationalTower(tower, minEnergy)) return false;
        if (tower.hasActiveNetworkReservation(gameTime)
                && !getBlockPos().equals(tower.supportReservedMasterPos)) return false;
        return !tower.isLiveMaster();
    }

    private boolean isLiveMaster() {
        return currentDepth == 0 && targetType == 1 && hasLiveRelayTarget();
    }

    private boolean hasActiveNetworkReservation(long gameTime) {
        return supportReservedMasterPos != null && Rules.isReservationActive(gameTime, supportReservedUntil);
    }

    private boolean hasActiveSupportReservation(long gameTime) {
        return hasActiveNetworkReservation(gameTime)
                && targetType == 2
                && masterPos != null
                && masterPos.equals(supportReservedMasterPos)
                && targetPos != null;
    }

    private static List<PrismTowerBlockEntity> findLinkedTowers(PrismTowerBlockEntity tower) {
        Level level = tower.getLevel();
        if (level == null || level.isClientSide) return List.of();

        List<PrismTowerBlockEntity> candidates = findIndexedTowers(
                level, tower.getBlockPos(), REMOTE_SUPPORT_NEIGHBOR_SCAN_RANGE);
        candidates.removeIf(candidate -> !canLink(tower, candidate));
        return candidates;
    }

    private static boolean canLink(PrismTowerBlockEntity a, PrismTowerBlockEntity b) {
        int range = Math.max(a.getNeighborScanRange(), b.getNeighborScanRange());
        return canShareNetwork(a, b)
                && isWithinSphericalRange(a.getBlockPos(), b.getBlockPos(), range);
    }

    private static boolean canShareNetwork(PrismTowerBlockEntity a, PrismTowerBlockEntity b) {
        Level level = a.getLevel();
        if (level == null || level != b.getLevel()) return false;
        UUID ownerA = a.getOwnerUuid();
        UUID ownerB = b.getOwnerUuid();
        if (ownerA == null || ownerB == null) return ownerA == null && ownerB == null;
        if (ownerA.equals(ownerB)) return true;
        if (a.getAccessMode() == TurretAccessMode.PRIVATE
                || b.getAccessMode() == TurretAccessMode.PRIVATE) return false;
        if (a.getAccessMode() == TurretAccessMode.PUBLIC
                && b.getAccessMode() == TurretAccessMode.PUBLIC) return true;

        return Rules.canNetworksLink(
                ownerA, resolveOwnerTeamName(level, a), a.getAccessMode(),
                ownerB, resolveOwnerTeamName(level, b), b.getAccessMode());
    }

    private static String resolveOwnerTeamName(Level level, PrismTowerBlockEntity tower) {
        UUID ownerUuid = tower.getOwnerUuid();
        if (ownerUuid == null) return "";

        Player onlineOwner = level instanceof ServerLevel serverLevel
                ? serverLevel.getServer().getPlayerList().getPlayer(ownerUuid)
                : level.getPlayerByUUID(ownerUuid);
        if (onlineOwner != null) {
            // An online UUID is authoritative, including having left the team since
            // this tower saved its last scoreboard name.
            return onlineOwner.getTeam() == null ? "" : onlineOwner.getTeam().getName();
        }

        String ownerName = tower.getOwnerName();
        if (!ownerName.isEmpty()) {
            PlayerTeam savedNameTeam = level.getScoreboard().getPlayersTeam(ownerName);
            if (savedNameTeam != null) return savedNameTeam.getName();
        }
        return "";
    }

    private static boolean isWithinSphericalRange(BlockPos a, BlockPos b, int radius) {
        return Rules.isWithinSphericalRange(
                (long) a.getX() - b.getX(),
                (long) a.getY() - b.getY(),
                (long) a.getZ() - b.getZ(),
                radius);
    }

    private static long squaredDistance(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Pure rules kept separate so unit tests do not initialize registered block entities. */
    static final class Rules {
        private Rules() {
        }

        static boolean isWithinSphericalRange(long dx, long dy, long dz, int radius) {
            return radius >= 0 && dx * dx + dy * dy + dz * dz <= (long) radius * radius;
        }

        static boolean isReservationActive(long gameTime, long reservedUntil) {
            return gameTime < reservedUntil;
        }

        static int getPotentialSupportScanLimit(double baseRange, boolean remoteSupport) {
            double maxRange = remoteSupport
                    ? REMOTE_SUPPORT_MAX_MONSTER_SCAN_RANGE
                    : MAX_MONSTER_SCAN_RANGE;
            double supportBonus = remoteSupport ? SUPPORT_RANGE_BONUS * 1.35 : SUPPORT_RANGE_BONUS;
            int supportsToMaxRange = (int) Math.ceil(Math.max(0.0, maxRange - baseRange) / supportBonus);
            return Math.min(SUPPORT_SCAN_CAP, supportsToMaxRange);
        }

        static double calculateEffectiveScanRange(double baseRange, int potentialSupports, boolean remoteSupport) {
            double maxRange = remoteSupport
                    ? REMOTE_SUPPORT_MAX_MONSTER_SCAN_RANGE
                    : MAX_MONSTER_SCAN_RANGE;
            double supportBonus = remoteSupport ? SUPPORT_RANGE_BONUS * 1.35 : SUPPORT_RANGE_BONUS;
            return Math.min(maxRange, baseRange + Math.max(0, potentialSupports) * supportBonus);
        }

        static int getOperatingEnergyThreshold(int masterCost, int slaveCost) {
            return Math.min(masterCost, slaveCost);
        }

        /**
         * Symmetric opt-in policy for automatic FE sharing. Two legacy unowned
         * towers remain compatible, but an owned tower never absorbs an unowned
         * one. Same-owner towers trust each other; otherwise both access modes
         * must independently permit the relationship.
         */
        static boolean canNetworksLink(UUID ownerA, String teamA, TurretAccessMode accessA,
                                       UUID ownerB, String teamB, TurretAccessMode accessB) {
            if (ownerA == null || ownerB == null) return ownerA == null && ownerB == null;
            if (ownerA.equals(ownerB)) return true;

            boolean sameTeam = teamA != null && !teamA.isEmpty() && teamA.equals(teamB);
            return accessAllowsNetworkPeer(accessA, sameTeam)
                    && accessAllowsNetworkPeer(accessB, sameTeam);
        }

        private static boolean accessAllowsNetworkPeer(TurretAccessMode accessMode, boolean sameTeam) {
            return switch (accessMode) {
                case PRIVATE -> false;
                case TEAM -> sameTeam;
                case PUBLIC -> true;
            };
        }

    }

    private record SupportNode(PrismTowerBlockEntity tower, int depth) {
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
    public void onClientFire(long firedAtGameTime, int firedTargetId, int firedTargetType, BlockPos firedTargetPos) {
        lastFireTime = firedAtGameTime;
        lastShotTargetType = firedTargetType;
        lastShotTargetId = firedTargetId;
        lastShotTargetPos = firedTargetPos == null ? null : firedTargetPos.immutable();
        lastShotImpactPos = null;
        lastShotSupportCount = 0;
        visualCountdown = getRemainingFireVisualTicks(firedAtGameTime);
        if (visualCountdown <= 0) {
            clearLastShotSnapshot();
            clearClientVisualTarget();
            return;
        }
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

    private static int comparePositions(BlockPos a, BlockPos b) {
        int compareX = Integer.compare(a.getX(), b.getX());
        if (compareX != 0) return compareX;
        int compareY = Integer.compare(a.getY(), b.getY());
        if (compareY != 0) return compareY;
        return Integer.compare(a.getZ(), b.getZ());
    }

    private boolean canRelayFrom(BlockPos relayPos, PrismTowerBlockEntity parent) {
        if (parent == this) return false;
        if (!isOperationalTower(parent, TurretConfig.PRISM_SLAVE_FIRE_COST.get())) return false;
        if (parent.currentDepth < 0 || parent.currentDepth >= MAX_DEPTH) return false;
        if (parent.masterPos == null) return false;
        if (level == null || !(level.getBlockEntity(parent.masterPos) instanceof PrismTowerBlockEntity master)
                || !canShareNetwork(master, this)) return false;
        if (parent.masterPos.equals(relayPos)) return false;
        if (!canLink(parent, this)) return false;
        return parent.hasLiveRelayTarget();
    }

    private int consumeSupportNetwork(Level level, BlockPos activeMasterPos) {
        int slaveCost = TurretConfig.PRISM_SLAVE_FIRE_COST.get();
        long gameTime = level.getGameTime();
        supportReservedMasterPos = activeMasterPos.immutable();
        supportReservedUntil = gameTime + RELAY_PULSE_INTERVAL;
        setChanged();

        Set<BlockPos> visited = new HashSet<>();
        Queue<SupportNode> queue = new ArrayDeque<>();
        int paidSupportCount = 0;
        int visitedNodes = 0;
        level.getProfiler().push("flux_turret:prism_transaction_bfs");
        try {
            visited.add(getBlockPos());
            queue.add(new SupportNode(this, 0));

            while (!queue.isEmpty() && paidSupportCount < SUPPORT_SCAN_CAP) {
                SupportNode node = queue.poll();
                visitedNodes++;
                if (node.depth >= MAX_DEPTH) continue;

                for (PrismTowerBlockEntity candidate : findLinkedTowers(node.tower)) {
                    if (paidSupportCount >= SUPPORT_SCAN_CAP) break;
                    // Pairwise-compatible relays must not bridge a private/team-only
                    // tower into an unrelated master's transaction.
                    if (!canShareNetwork(this, candidate)) continue;
                    BlockPos candidatePos = candidate.getBlockPos();
                    if (!visited.add(candidatePos)) continue;
                    if (!isOperationalTower(candidate, slaveCost)) continue;
                    if (candidate.hasActiveNetworkReservation(gameTime) || candidate.isLiveMaster()) continue;
                    if (!candidate.getEnergyStorage().consumeEnergy(slaveCost)) continue;

                    int depth = node.depth + 1;
                    candidate.activatePaidRelay(level, activeMasterPos, node.tower, depth, gameTime);
                    queue.add(new SupportNode(candidate, depth));
                    paidSupportCount++;
                }
            }
        } finally {
            level.getProfiler().pop();
            TurretPerformanceTracker.recordPrismBfs(level, visitedNodes);
        }
        return paidSupportCount;
    }

    private void activatePaidRelay(Level level, BlockPos activeMasterPos,
                                   PrismTowerBlockEntity parent, int depth, long gameTime) {
        supportReservedMasterPos = activeMasterPos.immutable();
        supportReservedUntil = gameTime + RELAY_PULSE_INTERVAL;
        currentDepth = depth;
        masterPos = activeMasterPos.immutable();
        targetType = 2;
        targetPos = parent.getBlockPos().immutable();
        targetId = -1;
        warmupTicks = 0;
        cachedSupportCount = 0;
        isFiring = true;
        visualHasEnergy = getEnergyStorage().getEnergyStored() >= getMinOperatingCost();

        Vec3 relayTarget = Vec3.atLowerCornerOf(targetPos).add(0.5, 3.125, 0.5);
        captureLastShot(targetType, targetId, targetPos, relayTarget, 0, gameTime);
        requestThrottledUpdate();
        sendFirePacket(relayTarget, List.of(), 0, 0.0f);
    }

    private void captureLastShot(int firedTargetType, int firedTargetId, BlockPos firedTargetPos,
                                 Vec3 impactPos, int supportCount, long gameTime) {
        lastFireTime = gameTime;
        lastShotTargetType = firedTargetType;
        lastShotTargetId = firedTargetId;
        lastShotTargetPos = firedTargetPos == null ? null : firedTargetPos.immutable();
        lastShotImpactPos = impactPos;
        lastShotSupportCount = Math.max(0, supportCount);
        setChanged();
    }

    private void clearLastShotSnapshot() {
        lastShotTargetType = 0;
        lastShotTargetId = -1;
        lastShotTargetPos = null;
        lastShotImpactPos = null;
        lastShotSupportCount = 0;
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
            int previousVisualCountdown = be.visualCountdown;
            be.baseClientTick(level);
            if (previousVisualCountdown > 0 && be.visualCountdown == 0) {
                be.clearClientVisualTarget();
            }
            return;
        }

        be.registerLoadedTower();
        be.flushThrottledUpdate();

        long gameTime = level.getGameTime();
        // Absolute position-derived phases remain staggered after chunk reloads.
        // The 100-tick pass refreshes energy/reservation-sensitive support range;
        // topology changes still bypass it at the next 20-tick neighbor pass.
        if (isPositionScheduledTick(gameTime, pos, POTENTIAL_SUPPORT_SCAN_INTERVAL)) {
            be.supportTreeDirty = true;
            be.cachedEffectiveRange = be.getEffectiveScanRange();
        }

        if (isPositionScheduledTick(gameTime, pos, NEIGHBOR_CACHE_INTERVAL)) {
            boolean topologyChanged = be.refreshNeighborCache(level, pos);
            if (topologyChanged || be.cachedEffectiveRange < 0) {
                be.cachedEffectiveRange = be.getEffectiveScanRange();
            }
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
            be.monsterCache = List.of();
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= be.getMinOperatingCost();
            if (be.currentDepth != prevDepth || be.targetType != prevTargetType || be.targetId != prevTargetId
                    || !java.util.Objects.equals(be.targetPos, prevTargetPos)
                    || !java.util.Objects.equals(be.masterPos, prevMasterPos)
                    || be.isFiring != prevFiring || be.visualHasEnergy != prevHasEnergy) {
                be.requestThrottledUpdate();
            }
            be.flushThrottledUpdate();
            return;
        }

        int storedEnergy = be.getEnergyStorage().getEnergyStored();
        boolean hasSlaveEnergy = storedEnergy >= TurretConfig.PRISM_SLAVE_FIRE_COST.get();
        boolean hasMasterEnergy = storedEnergy >= TurretConfig.PRISM_MASTER_FIRE_COST.get();
        boolean hasOperatingEnergy = hasSlaveEnergy || hasMasterEnergy;
        be.visualHasEnergy = hasOperatingEnergy;

        boolean coolingDownThisTick = be.attackCooldown > 0;
        if (coolingDownThisTick) {
            be.attackCooldown--;
            if (be.attackCooldown == 0
                    || isPositionScheduledTick(gameTime, pos, NEIGHBOR_CACHE_INTERVAL)) be.setChanged();
        }

        if (be.lastShotTargetType != 0 && be.getRemainingFireVisualTicks(be.lastFireTime) == 0) {
            be.clearLastShotSnapshot();
            be.setChanged();
        }
        if (be.supportReservedMasterPos != null && gameTime >= be.supportReservedUntil) {
            BlockPos expiredMaster = be.supportReservedMasterPos;
            be.supportReservedMasterPos = null;
            be.supportReservedUntil = Long.MIN_VALUE;
            if (be.targetType == 2 && expiredMaster.equals(be.masterPos)) {
                resetState(be);
            }
            be.setChanged();
        }

        if (be.hasActiveSupportReservation(gameTime)) {
            be.monsterCache = List.of();
            be.isFiring = true;
        } else if (be.hasActiveNetworkReservation(gameTime)
                && !pos.equals(be.supportReservedMasterPos)) {
            // A paid relay whose topology was invalidated (for example by a
            // redstone transition) remains unavailable to every master until the
            // original transaction window expires.
            be.monsterCache = List.of();
            be.isFiring = false;
        } else if (hasOperatingEnergy) {
            be.refreshMonsterCacheOnSchedule(level, pos);
            PrismTowerBlockEntity bestParent = hasSlaveEnergy ? be.findBestRelayParent(pos) : null;
            if (bestParent != null && (be.currentDepth != 0
                    || comparePositions(bestParent.getBlockPos(), pos) < 0)) {
                be.relayFrom(level, bestParent);
            } else {
                Mob closestMonster = be.findClosestMonster(level, pos);

                boolean isMasterPotential = false;
                if (hasMasterEnergy && closestMonster != null) {
                    double myDistSq = closestMonster.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                    isMasterPotential = !be.hasSuperiorMasterCandidate(level, pos, closestMonster, myDistSq);
                }

                if (isMasterPotential && closestMonster != null) {
                    be.currentDepth = 0;
                    be.masterPos = pos;
                    be.targetType = 1;
                    be.targetId = closestMonster.getId();
                    be.targetPos = null;

                    if (!coolingDownThisTick && be.attackCooldown <= 0) {
                        be.warmupTicks++;
                        if (be.warmupTicks >= WARMUP_TICKS) {
                            if (be.getEnergyStorage().consumeEnergy(TurretConfig.PRISM_MASTER_FIRE_COST.get())) {
                                be.cachedSupportCount = be.consumeSupportNetwork(level, pos);
                                int damageSupports = Math.min(be.cachedSupportCount, DAMAGE_SUPPORT_CAP);
                                float damage = TurretConfig.PRISM_DAMAGE.get().floatValue()
                                        * (1.0f + damageSupports * SUPPORT_DAMAGE_MULT);
                                if (be.hasUpgrade(TurretUpgradeType.FOCUSED_BEAM)) {
                                    damage *= 1.45f;
                                }
                                closestMonster.hurt(level.damageSources().magic(), damage);
                                List<Vec3> refractionPoints = be.hasUpgrade(TurretUpgradeType.REFRACTION_BEAM)
                                        ? be.refractBeam(level, closestMonster, damage * 0.38f)
                                        : List.of();
                                Vec3 targetPos = closestMonster.position().add(0, closestMonster.getBbHeight() * 0.5, 0);

                                // Sound pitch varies with support count (more supports = higher pitch)
                                float pitchBase = 0.6f + (damageSupports * 0.05f);
                                TurretVisualEffects.playTurretSound(level, pos, ModRegistry.PRISM_SHOOT.get(),
                                    0.25f, pitchBase, 0.08f);

                                be.isFiring = true;
                                be.attackCooldown = MASTER_COOLDOWN;
                                be.warmupTicks = 0;
                                be.captureLastShot(be.targetType, be.targetId, be.targetPos,
                                        targetPos, be.cachedSupportCount, gameTime);
                                be.sendFirePacket(targetPos, refractionPoints, 0, be.cachedSupportCount);
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
                    if (bestParent != null) {
                        be.relayFrom(level, bestParent);
                    } else {
                        resetState(be);
                    }
                }
            }
        } else {
            be.monsterCache = List.of();
            resetState(be);
        }

        boolean stateChanged = be.currentDepth != prevDepth
                || be.targetType != prevTargetType
                || be.targetId != prevTargetId
                || !java.util.Objects.equals(be.targetPos, prevTargetPos)
                || !java.util.Objects.equals(be.masterPos, prevMasterPos)
                || be.isFiring != prevFiring
                || be.cachedSupportCount != prevSupportCount;
        if (stateChanged) {
            be.requestThrottledUpdate();
        }
        if (be.visualHasEnergy != prevHasEnergy) {
            be.requestThrottledUpdate();
        }
        be.flushThrottledUpdate();
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
        PrismTowerBlockEntity best = null;
        for (PrismTowerBlockEntity candidate : neighborCache) {
            if (!canRelayFrom(pos, candidate)) continue;
            if (best == null || compareRelayParents(candidate, best, pos) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int compareRelayParents(PrismTowerBlockEntity a, PrismTowerBlockEntity b, BlockPos relayPos) {
        int depthCompare = Integer.compare(a.currentDepth, b.currentDepth);
        if (depthCompare != 0) return depthCompare;
        int distanceCompare = Double.compare(a.getBlockPos().distSqr(relayPos), b.getBlockPos().distSqr(relayPos));
        if (distanceCompare != 0) return distanceCompare;
        return comparePositions(a.getBlockPos(), b.getBlockPos());
    }

    private boolean hasSuperiorMasterCandidate(Level level, BlockPos selfPos, Mob target, double selfDistanceSq) {
        int masterFireCost = TurretConfig.PRISM_MASTER_FIRE_COST.get();
        long gameTime = level.getGameTime();
        for (PrismTowerBlockEntity candidate : neighborCache) {
            if (candidate == this || !canLink(this, candidate)
                    || !isOperationalTower(candidate, masterFireCost)) continue;

            BlockPos candidatePos = candidate.getBlockPos();
            if (candidate.hasActiveNetworkReservation(gameTime)
                    && !candidatePos.equals(candidate.supportReservedMasterPos)) continue;
            double candidateDistanceSq = target.distanceToSqr(
                    candidatePos.getX(), candidatePos.getY(), candidatePos.getZ());
            boolean outranksSelf = Math.abs(candidateDistanceSq - selfDistanceSq) < 1.0
                    ? comparePositions(candidatePos, selfPos) < 0
                    : candidateDistanceSq < selfDistanceSq;
            if (!outranksSelf) continue;

            // Raycast only after the cheap deterministic rank and operational checks.
            if (candidate.isValidTarget(target, level, candidatePos)) {
                return true;
            }
        }
        return false;
    }

    private void relayFrom(Level level, PrismTowerBlockEntity bestParent) {
        currentDepth = bestParent.currentDepth + 1;
        masterPos = bestParent.masterPos;
        targetType = 2;
        targetPos = bestParent.getBlockPos();
        targetId = -1;
        warmupTicks = 0;
        // This is only the prospective topology used for deterministic master
        // election. The master owns the actual FE transaction and visual pulse.
        isFiring = false;
    }

    private List<Vec3> refractBeam(Level level, Mob primaryTarget, float damage) {
        List<Vec3> effectPoints = new ArrayList<>();
        Vec3 primaryPos = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.5, 0);
        double range = hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT) ? 8.0 : 6.0;
        int maxTargets = hasUpgrade(TurretUpgradeType.REMOTE_SUPPORT) ? 4 : 3;
        List<Mob> refractionTargets = trackedEntityQuery(level, Mob.class,
                        primaryTarget.getBoundingBox().inflate(range), monster ->
                                monster != primaryTarget && isEnemyTarget(monster)
                                        && monster.position().distanceTo(primaryPos) <= range
        );
        refractionTargets.sort(Comparator
                .comparingDouble((Mob monster) -> monster.position().distanceToSqr(primaryPos))
                .thenComparingInt(Entity::getId));
        int targetCount = Math.min(maxTargets, refractionTargets.size());
        for (int i = 0; i < targetCount; i++) {
            Mob monster = refractionTargets.get(i);
            monster.hurt(level.damageSources().magic(), damage);
            Vec3 refractPos = monster.position().add(0, monster.getBbHeight() * 0.5, 0);
            effectPoints.add(refractPos);
            damage *= 0.82f;
        }
        return effectPoints;
    }

    public int getTargetType() {
        return targetType;
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public int getSupportCount() {
        return level != null && level.isClientSide ? visualSupportCount : cachedSupportCount;
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
            if (this.visualCountdown > 0) {
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.prism_tower.active"));
            }
            if (this.isVisuallyPowered()) {
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
