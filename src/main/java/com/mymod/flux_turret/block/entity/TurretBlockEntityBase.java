package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.item.TurretUpgradeLoadout;
import com.mymod.flux_turret.util.TurretPerformanceTracker;
import com.mymod.flux_turret.util.TurretTickSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public abstract class TurretBlockEntityBase extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Threat priority system: higher values = higher priority
    protected static final Map<EntityType<?>, Integer> THREAT_PRIORITY = new HashMap<>();
    static {
        THREAT_PRIORITY.put(EntityType.CREEPER, 100);
        THREAT_PRIORITY.put(EntityType.WITHER_SKELETON, 90);
        THREAT_PRIORITY.put(EntityType.BLAZE, 80);
        THREAT_PRIORITY.put(EntityType.WITCH, 70);
        THREAT_PRIORITY.put(EntityType.SKELETON, 60);
        THREAT_PRIORITY.put(EntityType.ZOMBIE, 40);
        THREAT_PRIORITY.put(EntityType.SPIDER, 30);
        THREAT_PRIORITY.put(EntityType.CAVE_SPIDER, 25);
    }

    public int visualCountdown = 0;
    public int visualTargetId = -1;
    public Vec3 visualCachedTargetPos = null;
    public boolean visualHasEnergy = false;

    protected int targetId = -1;
    protected int attackCooldown = 0;
    protected boolean isFiring = false;
    protected long lastFireTime = 0;
    protected List<Mob> monsterCache = List.of();
    private List<Mob> targetScoringSnapshot = List.of();
    private int upgradeMask = 0;
    private int activeWeaponUpgradeMask = 0;
    private int activeUtilityUpgradeMask = 0;
    private TargetingMode targetingMode = TargetingMode.AUTO;
    private RedstoneControlMode redstoneControlMode = RedstoneControlMode.DISABLE_WHEN_POWERED;
    private TurretAccessMode accessMode = TurretAccessMode.TEAM;
    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";
    private long nextTargetScanTime = Long.MIN_VALUE;
    private long lastMonsterCacheRefreshTime = Long.MIN_VALUE;

    // Keep an acquired target instead of ray-casting every candidate every tick.
    // Cheap liveness/range checks still run every tick; only the expensive path
    // validation is allowed to remain stale for this short interval.
    private static final int TARGET_PATH_RECHECK_INTERVAL = 5;
    private int pathValidatedTargetId = -1;
    private long nextTargetPathCheckTime = Long.MIN_VALUE;

    // Transient visual state can change every tick (notably Gatling spin-up). Keep
    // persistence dirty immediately, but coalesce full block-entity packets.
    private static final int NETWORK_SYNC_INTERVAL = 5;
    private boolean networkSyncPending = false;
    private long lastNetworkSyncTime = Long.MIN_VALUE;

    // Server-authoritative aim point (world coords of the current target). Synced to
    // clients so aiming models (Gatling, Grand Cannon) can orient correctly even when
    // the target entity itself isn't tracked by that client — in multiplayer a far
    // client may not have the mob loaded, and client-side getEntity() would return null,
    // freezing the barrel at its last pose. When a target is present the client prefers
    // the live entity (for smooth per-frame tracking) and falls back to this point.
    public float aimTargetX = 0f;
    public float aimTargetY = 0f;
    public float aimTargetZ = 0f;
    public boolean hasAimTarget = false;

    private final TurretEnergyStorage energyStorage;
    private LazyOptional<IEnergyStorage> energyHandler;

    protected static class TurretEnergyStorage extends EnergyStorage {
        public TurretEnergyStorage(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        public void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(energy, this.capacity));
        }

        public boolean consumeEnergy(int amount) {
            if (amount <= 0) return true;
            if (this.energy < amount) return false;
            this.energy -= amount;
            return true;
        }
    }

    protected TurretBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                    int capacity, int maxReceive) {
        super(type, pos, state);
        this.energyStorage = new TurretEnergyStorage(capacity, maxReceive) {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int received = super.receiveEnergy(maxReceive, simulate);
                if (received > 0 && !simulate)
                    setChanged();
                return received;
            }
        };
        this.energyHandler = LazyOptional.of(() -> energyStorage);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isPerformanceTrackedTurret()) TurretPerformanceTracker.registerTurret(this);
    }

    @Override
    public void onChunkUnloaded() {
        if (isPerformanceTrackedTurret()) TurretPerformanceTracker.unregisterTurret(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (isPerformanceTrackedTurret()) TurretPerformanceTracker.unregisterTurret(this);
        super.setRemoved();
    }

    /** Multi-block implementations override this so only their controller is counted. */
    protected boolean isPerformanceTrackedTurret() {
        return true;
    }

    protected abstract double getTargetRange();

    protected abstract double getEyeHeight();

    protected abstract int getTargetCacheInterval();

    protected abstract int getFiringVisualCountdown();

    protected abstract int getMinOperatingCost();

    protected TurretEnergyStorage getEnergyStorage() {
        return energyStorage;
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
    public void reviveCaps() {
        super.reviveCaps();
        energyHandler = LazyOptional.of(() -> energyStorage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("TargetId", targetId);
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putBoolean("IsFiring", isFiring);
        tag.putLong("LastFireTime", lastFireTime);
        tag.putBoolean("HasPower", visualHasEnergy);
        tag.putInt("UpgradeMask", upgradeMask);
        tag.putInt("ActiveWeaponUpgrade", activeWeaponUpgradeMask);
        tag.putInt("ActiveUtilityUpgrade", activeUtilityUpgradeMask);
        tag.putInt("TargetingMode", targetingMode.ordinal());
        tag.putInt("RedstoneControlMode", redstoneControlMode.ordinal());
        tag.putInt("AccessMode", accessMode.ordinal());
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        if (!ownerName.isEmpty()) tag.putString("OwnerName", ownerName);
        tag.putBoolean("HasAim", hasAimTarget);
        if (hasAimTarget) {
            tag.putFloat("AimX", aimTargetX);
            tag.putFloat("AimY", aimTargetY);
            tag.putFloat("AimZ", aimTargetZ);
        }
        saveAdditionalTurret(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt("Energy"));
        targetId = tag.getInt("TargetId");
        attackCooldown = tag.contains("AttackCooldown") ? Math.max(0, tag.getInt("AttackCooldown")) : 0;
        isFiring = tag.getBoolean("IsFiring");
        lastFireTime = tag.getLong("LastFireTime");
        visualHasEnergy = tag.getBoolean("HasPower");
        upgradeMask = tag.getInt("UpgradeMask");
        activeWeaponUpgradeMask = tag.contains("ActiveWeaponUpgrade")
                ? tag.getInt("ActiveWeaponUpgrade") : firstInstalledMask(TurretUpgradeType.Slot.WEAPON);
        activeUtilityUpgradeMask = tag.contains("ActiveUtilityUpgrade")
                ? tag.getInt("ActiveUtilityUpgrade") : firstInstalledMask(TurretUpgradeType.Slot.UTILITY);
        normalizeActiveUpgrades();
        targetingMode = TargetingMode.fromOrdinal(tag.getInt("TargetingMode"));
        redstoneControlMode = tag.contains("RedstoneControlMode")
                ? RedstoneControlMode.fromOrdinal(tag.getInt("RedstoneControlMode"))
                : RedstoneControlMode.DISABLE_WHEN_POWERED;
        accessMode = tag.contains("AccessMode")
                ? TurretAccessMode.fromOrdinal(tag.getInt("AccessMode")) : TurretAccessMode.TEAM;
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        hasAimTarget = tag.getBoolean("HasAim");
        if (hasAimTarget) {
            aimTargetX = tag.getFloat("AimX");
            aimTargetY = tag.getFloat("AimY");
            aimTargetZ = tag.getFloat("AimZ");
        }
        loadAdditionalTurret(tag);
    }

    protected void saveAdditionalTurret(CompoundTag tag) {
    }

    protected void loadAdditionalTurret(CompoundTag tag) {
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
        updateClientVisualStateAfterLoad(false);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag == null) return;

        // Thread-safe: ensure we're on the client side
        if (level != null && !level.isClientSide) {
            return; // Server should not receive this packet
        }

        boolean preserveFireVisual = visualCountdown > 0;
        load(tag);
        updateClientVisualStateAfterLoad(preserveFireVisual);
        handleDataPacketAdditional(tag);
    }

    protected void updateClientVisualStateAfterLoad(boolean preserveFireVisual) {
        visualHasEnergy = energyStorage.getEnergyStored() >= getMinOperatingCost();
        if (preserveFireVisual) return;

        visualTargetId = targetId;
        visualCachedTargetPos = null;
        visualCountdown = 0;

        if (isFiring && targetId != -1) {
            visualCountdown = getFiringVisualCountdown();
            if (level != null) {
                Entity target = level.getEntity(targetId);
                if (target != null)
                    visualCachedTargetPos = target.getEyePosition(0.0f);
            }
        }
    }

    protected void handleDataPacketAdditional(CompoundTag tag) {
    }

    /** Cheap checks that are safe to run every tick for a retained target. */
    protected static boolean isEnemyTarget(Mob mob) {
        if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isRemoved()) return false;
        return !TurretConfig.FRIENDLY_FIRE_PROTECTION.get() || !mob.hasCustomName();
    }

    protected boolean isTargetUsable(Mob monster, BlockPos selfPos) {
        if (!isEnemyTarget(monster)) return false;

        double range = getTargetRange();
        double dx = monster.getX() - (selfPos.getX() + 0.5D);
        double dy = monster.getEyeY() - (selfPos.getY() + getEyeHeight());
        double dz = monster.getZ() - (selfPos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    protected boolean isValidTarget(Mob monster, Level level, BlockPos selfPos) {
        if (!isTargetUsable(monster, selfPos)) return false;
        Vec3 eyePos = new Vec3(selfPos.getX() + 0.5, selfPos.getY() + getEyeHeight(), selfPos.getZ() + 0.5);
        Vec3 targetEye = monster.getEyePosition(0.0f);
        TurretPerformanceTracker.recordRaycast(level);
        level.getProfiler().push("flux_turret:raycast");
        BlockHitResult hitResult;
        try {
            hitResult = level.clip(new ClipContext(
                    eyePos, targetEye,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    null));
        } finally {
            level.getProfiler().pop();
        }
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double hitDistSq = hitResult.getLocation().distanceToSqr(eyePos);
            double targetDistSq = targetEye.distanceToSqr(eyePos);
            if (hitDistSq < targetDistSq - 1.0) return false;
        }
        return true;
    }

    protected void refreshMonsterCache(Level level, BlockPos pos) {
        level.getProfiler().push("flux_turret:target_cache_refresh");
        try {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + getEyeHeight();
            double z = pos.getZ() + 0.5;
            double range = getTargetRange();
            AABB scanArea = new AABB(
                    x - range, y - range, z - range,
                    x + range, y + range, z + range);

            // Minecraft's native entity index already performs a single section-aware
            // AABB query. Precise spherical range and friendly-fire checks are applied in
            // its predicate, avoiding the former per-column, full-build-height scans.
            monsterCache = trackedEntityQuery(level, Mob.class, scanArea,
                    monster -> isTargetUsable(monster, pos));
            lastMonsterCacheRefreshTime = level.getGameTime();

            if (getEffectiveTargetingMode() == TargetingMode.CLUSTER) {
                int scoringSize = Math.min(96, monsterCache.size());
                targetScoringSnapshot = List.copyOf(monsterCache.subList(0, scoringSize));
            }
            monsterCache.sort(createTargetComparator(x, y, z));
        } finally {
            targetScoringSnapshot = List.of();
            level.getProfiler().pop();
        }
    }

    /**
     * One instrumented entity query shared by targeting and damage-area scans.
     * The returned-list size is the useful candidate count; querying a broader list
     * just to measure rejected entities would itself make the hot path slower.
     */
    protected static <T extends Entity> List<T> trackedEntityQuery(
            Level level, Class<T> entityClass, AABB area, Predicate<? super T> predicate) {
        level.getProfiler().push("flux_turret:entity_scan");
        List<T> result;
        try {
            result = level.getEntitiesOfClass(entityClass, area, predicate);
        } finally {
            level.getProfiler().pop();
        }
        TurretPerformanceTracker.recordEntityScan(level, result.size());
        return result;
    }

    /** Builds a deterministic comparator while preserving each turret's AUTO role. */
    protected Comparator<Mob> createTargetComparator(double x, double y, double z) {
        TargetingMode effective = getEffectiveTargetingMode();
        Comparator<Mob> primary = switch (effective) {
            case HIGHEST_HEALTH -> Comparator.comparingDouble((Mob mob) -> -mob.getHealth());
            case FASTEST -> Comparator.comparingDouble((Mob mob) ->
                    -mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            case HIGHEST_ARMOR -> Comparator.comparingInt((Mob mob) -> -mob.getArmorValue());
            case CLUSTER -> Comparator.comparingInt((Mob mob) -> -countCachedEnemiesNear(mob, 5.0D));
            case BEACON_WAVE -> Comparator.comparingInt((Mob mob) ->
                    mob.getPersistentData().getBoolean("FluxTurretBeaconSpawn") ? 0 : 1);
            case AUTO, NEAREST -> Comparator.comparingDouble(mob -> mob.distanceToSqr(x, y, z));
        };
        return primary
                .thenComparingInt(mob -> -THREAT_PRIORITY.getOrDefault(mob.getType(), 10))
                .thenComparingDouble(mob -> mob.distanceToSqr(x, y, z))
                .thenComparingInt(Entity::getId);
    }

    protected TargetingMode getAutomaticTargetingMode() {
        return TargetingMode.NEAREST;
    }

    private TargetingMode getEffectiveTargetingMode() {
        return targetingMode == TargetingMode.AUTO ? getAutomaticTargetingMode() : targetingMode;
    }

    private int countCachedEnemiesNear(Mob center, double radius) {
        double radiusSqr = radius * radius;
        int count = 0;
        // A hard cap keeps pathological mob farms from turning one sort into an
        // unbounded O(n^2) spike. Counts only choose a target; damage is unaffected.
        for (Mob candidate : targetScoringSnapshot) {
            if (candidate.isAlive() && candidate.distanceToSqr(center) <= radiusSqr) count++;
        }
        return count;
    }

    protected Mob findClosestMonster(Level level, BlockPos pos) {
        // Prefer the current target while it remains alive and inside the true
        // spherical range. This prevents target churn and avoids a LOS clip every
        // tick; obstructions are still noticed within a few ticks.
        int rejectedTargetId = -1;
        if (targetId != -1) {
            rejectedTargetId = targetId;
            Entity entity = level.getEntity(targetId);
            if (entity instanceof Mob retained && isTargetUsable(retained, pos)) {
                long now = level.getGameTime();
                if (pathValidatedTargetId == targetId && now < nextTargetPathCheckTime) {
                    return retained;
                }
                if (isValidTarget(retained, level, pos)) {
                    rememberPathValidation(retained, now);
                    return retained;
                }
            }
        }

        // A retained target may fail its periodic path validation between regular
        // scan phases. Refresh immediately before selecting a replacement so the
        // stable-target scan elision below never adds another cache interval of
        // response latency.
        if (rejectedTargetId != -1 && lastMonsterCacheRefreshTime != level.getGameTime()) {
            refreshMonsterCache(level, pos);
            scheduleNextTargetScan(level.getGameTime() + 1L, pos, getTargetCacheInterval());
        }

        pathValidatedTargetId = -1;
        nextTargetPathCheckTime = Long.MIN_VALUE;
        for (Mob monster : monsterCache) {
            if (monster.getId() == rejectedTargetId) continue;
            if (!isTargetUsable(monster, pos)) continue;
            if (isValidTarget(monster, level, pos)) {
                rememberPathValidation(monster, level.getGameTime());
                return monster;
            }
        }

        return null;
    }

    private void rememberPathValidation(Mob monster, long now) {
        pathValidatedTargetId = monster.getId();
        nextTargetPathCheckTime = now + TARGET_PATH_RECHECK_INTERVAL;
    }

    protected boolean isRedstoneBlocked(Level level, BlockPos pos) {
        return redstoneControlMode.blocks(hasRedstoneSignal(level, pos));
    }

    protected boolean isSignalBlocked(boolean hasSignal) {
        return redstoneControlMode.blocks(hasSignal);
    }

    /**
     * The physical signal used by both operation checks and diagnostics. Multi-block
     * turrets override the level-aware form so every consumer sees the same aggregate.
     */
    public boolean hasRedstoneSignal() {
        return level != null && hasRedstoneSignal(level, worldPosition);
    }

    protected boolean hasRedstoneSignal(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    protected void baseClientTick(Level level) {
        if (level.isClientSide) {
            if (visualCountdown > 0) {
                visualCountdown--;
                if (visualCountdown == 0) {
                    visualTargetId = targetId;
                    visualCachedTargetPos = null;
                }
            }
        }
    }

    protected void refreshMonsterCacheIfNeeded(Level level, BlockPos pos) {
        refreshMonsterCacheOnSchedule(level, pos);
    }

    /** Scheduled variant for turrets that maintain their own non-target tick phases. */
    protected void refreshMonsterCacheOnSchedule(Level level, BlockPos pos) {
        int interval = Math.max(1, getTargetCacheInterval());
        long now = level.getGameTime();
        if (nextTargetScanTime == Long.MIN_VALUE) {
            nextTargetScanTime = nextScheduledTick(now, pos, interval);
        }

        if (now < nextTargetScanTime) return;

        // findClosestMonster always retains a usable target, so refreshing the full
        // candidate list while it is held cannot change this tick's choice. The
        // moment it dies, leaves range, or fails LOS, an immediate scan is forced.
        if (targetId == -1) {
            refreshMonsterCache(level, pos);
        }
        scheduleNextTargetScan(now + 1L, pos, interval);
    }

    private void scheduleNextTargetScan(long fromGameTime, BlockPos pos, int interval) {
        nextTargetScanTime = nextScheduledTick(fromGameTime, pos, Math.max(1, interval));
    }

    /** Stable absolute phase: reload order and simultaneous chunk load cannot re-align work. */
    protected static boolean isPositionScheduledTick(long gameTime, BlockPos pos, int interval) {
        return TurretTickSchedule.isScheduledTick(gameTime, pos.asLong(), interval);
    }

    protected static long nextScheduledTick(long gameTime, BlockPos pos, int interval) {
        return TurretTickSchedule.nextScheduledTick(gameTime, pos.asLong(), interval);
    }

    public long getLastFireTime() {
        return lastFireTime;
    }

    /**
     * Client-side: invoked by {@link com.mymod.flux_turret.network.TurretFirePacket}
     * once per shot to drive the firing beam window and animation countdown
     * without a full block-entity resync. The target is taken from the packet
     * (server-authoritative at fire time), not the local mirror, so visuals stay
     * correct even if the fire packet arrives before the next block-entity sync.
     */
    public void onClientFire(long firedAtGameTime, int firedTargetId, int firedTargetType,
                             @Nullable BlockPos firedTargetPos) {
        lastFireTime = firedAtGameTime;
        visualCountdown = getRemainingFireVisualTicks(firedAtGameTime);
        visualTargetId = visualCountdown > 0 ? firedTargetId : targetId;
    }

    protected int getRemainingFireVisualTicks(long firedAtGameTime) {
        int duration = Math.max(0, getFiringVisualCountdown());
        if (level == null) return duration;
        long elapsed = Math.max(0L, level.getGameTime() - firedAtGameTime);
        return Math.max(0, duration - (int) Math.min(Integer.MAX_VALUE, elapsed));
    }

    /** Target type to ship in the fire packet (0 = simple entity target). Overridden by relay turrets. */
    protected int getFireTargetType() {
        return 0;
    }

    /** Target position to ship in the fire packet (null for simple entity targets). Overridden by relay turrets. */
    @Nullable
    protected BlockPos getFireTargetPos() {
        return null;
    }

    /** Server-side: notify tracking clients that a shot was fired this tick. */
    protected void sendFirePacket() {
        Vec3 impactPos = null;
        if (level != null && targetId != -1) {
            Entity target = level.getEntity(targetId);
            if (target != null) {
                impactPos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            }
        }
        sendFirePacket(impactPos, List.of(), 0, 0.0f);
    }

    /**
     * Server-side: send one compact visual event for the complete shot. Effect
     * points are encoded as floats relative to the turret, keeping even chained
     * shots substantially smaller than broadcasting every particle separately.
     */
    protected void sendFirePacket(@Nullable Vec3 impactPos, List<Vec3> secondaryEffectPoints,
                                  int effectFlags, float effectStrength) {
        if (level == null || level.isClientSide || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        serverLevel.getProfiler().push("flux_turret:fire_packet");
        try {
            net.minecraft.world.level.chunk.LevelChunk chunk = serverLevel.getChunkAt(worldPosition);
            com.mymod.flux_turret.network.ModNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                    new com.mymod.flux_turret.network.TurretFirePacket(
                            worldPosition, serverLevel.getGameTime(),
                            targetId, getFireTargetType(), getFireTargetPos(),
                            impactPos, secondaryEffectPoints, effectFlags, effectStrength));
            TurretPerformanceTracker.recordFirePacket(serverLevel);
        } finally {
            serverLevel.getProfiler().pop();
        }
    }

    public int getTargetId() {
        return targetId;
    }

    // Aim point last pushed to clients; lets us resync only when the barrel would
    // visibly drift, instead of every tick a target moves.
    private float syncedAimX = 0f;
    private float syncedAimY = 0f;
    private float syncedAimZ = 0f;
    private boolean syncedHasAim = false;
    private static final double AIM_RESYNC_DISTANCE_SQR = 1.5 * 1.5;

    /**
     * True when the aim point has changed enough since the last sync to be worth
     * pushing again: the target appeared/disappeared, or moved more than ~1.5 blocks.
     * A moving target changes aim every tick, but the fallback orientation doesn't
     * need per-tick precision, so this caps aim-driven resyncs.
     */
    protected boolean aimDriftedSinceSync() {
        if (hasAimTarget != syncedHasAim) return true;
        if (!hasAimTarget) return false;
        double dx = aimTargetX - syncedAimX;
        double dy = aimTargetY - syncedAimY;
        double dz = aimTargetZ - syncedAimZ;
        return dx * dx + dy * dy + dz * dz > AIM_RESYNC_DISTANCE_SQR;
    }

    /** Mark dirty and push a block-entity sync to tracking clients immediately. */
    protected void markUpdated() {
        setChanged();
        networkSyncPending = false;
        syncedAimX = aimTargetX;
        syncedAimY = aimTargetY;
        syncedAimZ = aimTargetZ;
        syncedHasAim = hasAimTarget;
        if (level != null) {
            lastNetworkSyncTime = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Request a full client sync, coalescing repeated tick-level changes. */
    protected void requestThrottledUpdate() {
        setChanged();
        networkSyncPending = true;
    }

    /** Flush a pending full sync once the short coalescing interval has elapsed. */
    protected void flushThrottledUpdate() {
        if (!networkSyncPending || level == null || level.isClientSide) return;
        long now = level.getGameTime();
        if (lastNetworkSyncTime == Long.MIN_VALUE || now - lastNetworkSyncTime >= NETWORK_SYNC_INTERVAL) {
            markUpdated();
        }
    }

    public boolean isVisuallyPowered() {
        return visualHasEnergy || energyStorage.getEnergyStored() >= getMinOperatingCost();
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getEnergyCapacity() {
        return energyStorage.getMaxEnergyStored();
    }

    public int getOperatingEnergyCost() {
        return getMinOperatingCost();
    }

    public double getConfiguredRange() {
        return getTargetRange();
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public boolean isFiringNow() {
        return isFiring;
    }

    protected boolean isStructureValidForDiagnostics() {
        return true;
    }

    protected boolean isWarmingUpForDiagnostics() {
        return false;
    }

    protected boolean hasTargetForDiagnostics() {
        return targetId != -1;
    }

    public TurretStatus getOperationalStatus() {
        if (!isStructureValidForDiagnostics()) return TurretStatus.STRUCTURE_INVALID;
        if (level != null && isRedstoneBlocked(level, worldPosition)) return TurretStatus.REDSTONE_STOP;
        if (energyStorage.getEnergyStored() < getMinOperatingCost()) return TurretStatus.NO_ENERGY;
        if (isFiring) return TurretStatus.FIRING;
        if (isWarmingUpForDiagnostics()) return TurretStatus.WARMING_UP;
        if (!hasTargetForDiagnostics()) return TurretStatus.NO_TARGET;
        if (attackCooldown > 0) return TurretStatus.COOLDOWN;
        return TurretStatus.TRACKING;
    }

    /**
     * Server-side: record the world-space point this turret is aiming at, so the
     * client can orient the model even when the target entity isn't loaded on that
     * client (multiplayer, entity outside the player's tracking range). Stored as
     * the raw target point rather than yaw/pitch because each turret model rotates
     * around its own pivot; the model computes its own angles from this point.
     */
    protected void setAimTarget(double x, double y, double z) {
        this.aimTargetX = (float) x;
        this.aimTargetY = (float) y;
        this.aimTargetZ = (float) z;
        this.hasAimTarget = true;
    }

    /** Server-side: clear the aim point when the turret has no target. */
    protected void clearAimTarget() {
        this.hasAimTarget = false;
    }

    public boolean hasAimTarget() {
        return hasAimTarget;
    }

    public float getAimTargetX() {
        return aimTargetX;
    }

    public float getAimTargetY() {
        return aimTargetY;
    }

    public float getAimTargetZ() {
        return aimTargetZ;
    }

    public boolean hasUpgrade(TurretUpgradeType type) {
        int activeMask = type.getSlot() == TurretUpgradeType.Slot.WEAPON
                ? activeWeaponUpgradeMask : activeUtilityUpgradeMask;
        return (upgradeMask & type.getMask()) != 0 && (activeMask & type.getMask()) != 0;
    }

    public boolean hasInstalledUpgrade(TurretUpgradeType type) {
        return (upgradeMask & type.getMask()) != 0;
    }

    /** Client-visible: any upgrade module installed? Drives the pulsing upgrade glow layer. */
    public boolean hasAnyUpgrade() {
        return upgradeMask != 0;
    }

    public void installUpgrade(TurretUpgradeType type) {
        upgradeMask |= type.getMask();
        if (type.getSlot() == TurretUpgradeType.Slot.WEAPON && activeWeaponUpgradeMask == 0) {
            activeWeaponUpgradeMask = type.getMask();
        } else if (type.getSlot() == TurretUpgradeType.Slot.UTILITY && activeUtilityUpgradeMask == 0) {
            activeUtilityUpgradeMask = type.getMask();
        }
        onUpgradeLoadoutChanged();
        markUpdated();
    }

    protected void onUpgradeLoadoutChanged() {
    }

    public int getInstalledUpgradeMask() {
        return upgradeMask;
    }

    public int getActiveWeaponUpgradeMask() {
        return activeWeaponUpgradeMask;
    }

    public int getActiveUtilityUpgradeMask() {
        return activeUtilityUpgradeMask;
    }

    public void cycleActiveUpgrade(TurretUpgradeType.Slot slot) {
        List<TurretUpgradeType> installed = new ArrayList<>();
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if (type.getSlot() == slot && hasInstalledUpgrade(type) && canInstallUpgrade(type)) {
                installed.add(type);
            }
        }
        if (installed.isEmpty()) return;

        int currentMask = slot == TurretUpgradeType.Slot.WEAPON
                ? activeWeaponUpgradeMask : activeUtilityUpgradeMask;
        int currentIndex = -1;
        for (int i = 0; i < installed.size(); i++) {
            if ((currentMask & installed.get(i).getMask()) != 0) {
                currentIndex = i;
                break;
            }
        }
        int nextMask = installed.get((currentIndex + 1) % installed.size()).getMask();
        if (slot == TurretUpgradeType.Slot.WEAPON) activeWeaponUpgradeMask = nextMask;
        else activeUtilityUpgradeMask = nextMask;
        onUpgradeLoadoutChanged();
        markUpdated();
    }

    /**
     * Atomically removes every installed module and returns the represented types.
     * Callers use this both for explicit player recovery and for block teardown, so
     * nested multi-block removal cannot duplicate module drops.
     */
    public List<TurretUpgradeType> removeAllUpgrades() {
        if (upgradeMask == 0) {
            return List.of();
        }

        List<TurretUpgradeType> removed = new ArrayList<>();
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if ((upgradeMask & type.getMask()) != 0) {
                removed.add(type);
            }
        }
        upgradeMask = 0;
        activeWeaponUpgradeMask = 0;
        activeUtilityUpgradeMask = 0;
        onUpgradeLoadoutChanged();
        setChanged();
        return removed;
    }

    private int firstInstalledMask(TurretUpgradeType.Slot slot) {
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if (type.getSlot() == slot && (upgradeMask & type.getMask()) != 0 && canInstallUpgrade(type)) {
                return type.getMask();
            }
        }
        return 0;
    }

    private void normalizeActiveUpgrades() {
        activeWeaponUpgradeMask = TurretUpgradeLoadout.normalizeActiveMask(activeWeaponUpgradeMask, upgradeMask,
                TurretUpgradeType.Slot.WEAPON, this::canInstallUpgrade);
        activeUtilityUpgradeMask = TurretUpgradeLoadout.normalizeActiveMask(activeUtilityUpgradeMask, upgradeMask,
                TurretUpgradeType.Slot.UTILITY, this::canInstallUpgrade);
    }

    public TargetingMode getTargetingMode() {
        return targetingMode;
    }

    public void cycleTargetingMode() {
        targetingMode = targetingMode.next();
        nextTargetScanTime = Long.MIN_VALUE;
        targetId = -1;
        monsterCache = List.of();
        markUpdated();
    }

    public RedstoneControlMode getRedstoneControlMode() {
        return redstoneControlMode;
    }

    public void cycleRedstoneControlMode() {
        redstoneControlMode = redstoneControlMode.next();
        markUpdated();
    }

    public TurretAccessMode getAccessMode() {
        return accessMode;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean claimIfUnowned(Player player) {
        if (ownerUuid != null || player instanceof FakePlayer) return false;
        ownerUuid = player.getUUID();
        ownerName = player.getScoreboardName();
        markUpdated();
        return true;
    }

    public boolean canPlayerConfigure(Player player) {
        if (player instanceof FakePlayer) return false;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.hasPermissions(2)) return true;
        if (ownerUuid == null || ownerUuid.equals(player.getUUID())) return true;
        if (accessMode == TurretAccessMode.PUBLIC) return true;
        if (accessMode != TurretAccessMode.TEAM || level == null) return false;

        Player owner = level.getPlayerByUUID(ownerUuid);
        if (owner != null && player.isAlliedTo(owner)) return true;
        if (!ownerName.isEmpty() && player.getTeam() != null) {
            net.minecraft.world.scores.PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
            return ownerTeam != null && ownerTeam == player.getTeam();
        }
        return false;
    }

    public boolean canPlayerChangeAccess(Player player) {
        return !(player instanceof FakePlayer)
                && (ownerUuid == null || ownerUuid.equals(player.getUUID())
                || player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.hasPermissions(2));
    }

    public void cycleAccessMode(Player player) {
        if (!canPlayerChangeAccess(player)) return;
        claimIfUnowned(player);
        accessMode = accessMode.next();
        markUpdated();
    }

    /** NBT that is safe to carry with a relocated block item; modules drop separately. */
    public void savePortableData(CompoundTag tag) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("TargetingMode", targetingMode.ordinal());
        tag.putInt("RedstoneControlMode", redstoneControlMode.ordinal());
        tag.putInt("AccessMode", accessMode.ordinal());
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        if (!ownerName.isEmpty()) tag.putString("OwnerName", ownerName);
        savePortableDataAdditional(tag);
    }

    protected void savePortableDataAdditional(CompoundTag tag) {
    }

    public boolean canInstallUpgrade(TurretUpgradeType type) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
