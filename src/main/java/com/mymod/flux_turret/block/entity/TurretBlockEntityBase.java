package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.TurretUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    protected int tickCounter = 0;
    protected List<Monster> monsterCache = List.of();
    private int upgradeMask = 0;

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
        tag.putBoolean("IsFiring", isFiring);
        tag.putLong("LastFireTime", lastFireTime);
        tag.putBoolean("HasPower", visualHasEnergy);
        tag.putInt("UpgradeMask", upgradeMask);
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
        isFiring = tag.getBoolean("IsFiring");
        lastFireTime = tag.getLong("LastFireTime");
        visualHasEnergy = tag.getBoolean("HasPower");
        upgradeMask = tag.getInt("UpgradeMask");
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

        // Safe to update on client
        load(tag);
        visualHasEnergy = energyStorage.getEnergyStored() >= getMinOperatingCost();
        visualTargetId = targetId;
        visualCachedTargetPos = null;

        if (isFiring && targetId != -1) {
            visualCountdown = getFiringVisualCountdown();
            if (level != null) {
                Entity target = level.getEntity(targetId);
                if (target != null)
                    visualCachedTargetPos = target.getEyePosition(0.0f);
            }
        } else {
            visualCountdown = 0;
        }

        handleDataPacketAdditional(tag);
    }

    protected void handleDataPacketAdditional(CompoundTag tag) {
    }

    protected boolean isValidTarget(Monster monster, Level level, BlockPos selfPos) {
        if (!monster.isAlive()) return false;
        if (TurretConfig.FRIENDLY_FIRE_PROTECTION.get() && monster.hasCustomName()) return false;
        Vec3 eyePos = new Vec3(selfPos.getX() + 0.5, selfPos.getY() + getEyeHeight(), selfPos.getZ() + 0.5);
        Vec3 targetEye = monster.getEyePosition(0.0f);
        BlockHitResult hitResult = level.clip(new ClipContext(
                eyePos, targetEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null));
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double hitDistSq = hitResult.getLocation().distanceToSqr(eyePos);
            double targetDistSq = targetEye.distanceToSqr(eyePos);
            if (hitDistSq < targetDistSq - 1.0) return false;
        }
        return true;
    }

    protected void refreshMonsterCache(Level level, BlockPos pos) {
        // Read the config flag once per scan rather than once per candidate entity.
        final boolean friendlyFire = TurretConfig.FRIENDLY_FIRE_PROTECTION.get();
        AABB scanArea = new AABB(pos).inflate(getTargetRange());

        // On the server, source candidates from the per-tick shared column index so
        // overlapping turrets don't each re-scan the same region. The cache returns
        // the same monsters a direct AABB query would; we still apply the alive /
        // friendly-fire predicate here so the filtered set is identical. Fall back to
        // a direct scan off the server thread (should not happen for tick logic).
        List<Monster> candidates;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            candidates = com.mymod.flux_turret.util.TurretScanCache.get(serverLevel).query(serverLevel, scanArea);
        } else {
            candidates = level.getEntitiesOfClass(Monster.class, scanArea);
        }

        List<Monster> filtered = new java.util.ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Monster m = candidates.get(i);
            if (m.isAlive() && !m.isRemoved() && (!friendlyFire || !m.hasCustomName())) {
                filtered.add(m);
            }
        }
        monsterCache = filtered;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + getEyeHeight();
        double z = pos.getZ() + 0.5;

        // Sort by threat priority first, then by distance
        monsterCache.sort(Comparator
                .comparingInt((Monster m) -> -THREAT_PRIORITY.getOrDefault(m.getType(), 10))
                .thenComparingDouble(m -> m.distanceToSqr(x, y, z)));
    }

    protected Monster findClosestMonster(Level level, BlockPos pos) {
        for (Monster monster : monsterCache) {
            if (monster == null || !monster.isAlive() || monster.isRemoved()) continue;
            if (isValidTarget(monster, level, pos)) return monster;
        }

        return null;
    }

    protected boolean isRedstoneBlocked(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
    }

    protected void baseClientTick(Level level) {
        if (level.isClientSide) {
            if (visualCountdown > 0)
                visualCountdown--;
        }
    }

    protected void refreshMonsterCacheIfNeeded(Level level, BlockPos pos) {
        tickCounter++;
        int interval = Math.max(1, getTargetCacheInterval());
        if (tickCounter == 1 || tickCounter % interval == 0)
            refreshMonsterCache(level, pos);
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
    public void onClientFire(long gameTime, int firedTargetId, int firedTargetType, @Nullable BlockPos firedTargetPos) {
        lastFireTime = gameTime;
        visualCountdown = getFiringVisualCountdown();
        visualTargetId = firedTargetId;
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
        if (level == null || level.isClientSide || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        net.minecraft.world.level.chunk.LevelChunk chunk = serverLevel.getChunkAt(worldPosition);
        com.mymod.flux_turret.network.ModNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                new com.mymod.flux_turret.network.TurretFirePacket(
                        worldPosition, targetId, getFireTargetType(), getFireTargetPos()));
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

    /** Mark dirty and push a block-entity sync to tracking clients. */
    protected void markUpdated() {
        setChanged();
        syncedAimX = aimTargetX;
        syncedAimY = aimTargetY;
        syncedAimZ = aimTargetZ;
        syncedHasAim = hasAimTarget;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isVisuallyPowered() {
        return visualHasEnergy || energyStorage.getEnergyStored() >= getMinOperatingCost();
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
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
        return (upgradeMask & type.getMask()) != 0;
    }

    /** Client-visible: any upgrade module installed? Drives the pulsing upgrade glow layer. */
    public boolean hasAnyUpgrade() {
        return upgradeMask != 0;
    }

    public void installUpgrade(TurretUpgradeType type) {
        upgradeMask |= type.getMask();
        markUpdated();
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
