package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.PsychicBeaconBlock;
import com.mymod.flux_turret.menu.PsychicBeaconMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

public class PsychicBeaconBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final int MAX_RECEIVE = 1000;
    private static final String BEACON_SPAWN_TAG = "FluxTurretBeaconSpawn";
    private static final String BEACON_POS_TAG = "FluxTurretBeaconPos";
    private static final int MONSTER_COUNT_RADIUS = 32;
    private static final int NOTIFY_RADIUS = 50;
    private static final int SPAWN_CLEANUP_RADIUS = 48;
    private static final int NETWORK_NODE_SYNC_CAP = 24;
    private static final int DEFENSE_START_TIME = 12000;
    private static final int DAWN_TIME = 23000;
    private static final String REWARD_CHEST_ID_TAG = "FluxTurretRewardId";

    public static final int STATE_OFFLINE = 0;
    public static final int STATE_IDLE = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_FAILED = 3;
    public static final int STATE_WARNING = 4;
    public static final int BUFF_SPEED = 0;
    public static final int BUFF_HASTE = 1;
    public static final int BUFF_RESISTANCE = 2;
    public static final int BUFF_STRENGTH = 3;
    public static final int BUFF_REGENERATION = 4;
    public static final int BUFF_COUNT = 5;
    public static final int DOCTRINE_GUARD = 0;
    public static final int DOCTRINE_LURE = 1;
    public static final int DOCTRINE_CONTROL = 2;
    public static final int DOCTRINE_COUNT = 3;
    public static final int AFFIX_NONE = 0;
    public static final int AFFIX_ARMORED = 1;
    public static final int AFFIX_SWARM = 2;
    public static final int AFFIX_RUSH = 3;
    public static final int AFFIX_OVERLOAD = 4;
    public static final int AFFIX_COUNT = 5;

    private int beaconState = STATE_OFFLINE;
    private int stability = 100;
    private int threatLevel = 0;
    private int spawnTimer = 0;
    private int warningTimer = 0;
    private int scanCooldown = 0;
    private int todayKills = 0;
    // Kept for backwards-compatible NBT reads. Dawn settlement is now driven by
    // the battle session instead of a narrow world-time window.
    private boolean dawnProcessed = false;
    private boolean enabled = true;
    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";
    private int selectedBuffMask = 1 << BUFF_SPEED;
    private int stabilityNoticeStage = 0;
    private int doctrine = DOCTRINE_GUARD;
    private int activeWaveAffix = AFFIX_NONE;
    private int lastBattleScore = 0;

    // A night is a persisted, server-authoritative session. Snapshotting these
    // values prevents changing the pyramid or doctrine after an easy fight and
    // then claiming high-tier rewards.
    private boolean battleInProgress = false;
    private boolean battleEligible = false;
    private int battleThreatLevel = 0;
    private int battleDoctrine = DOCTRINE_GUARD;
    private int battleAffix = AFFIX_NONE;
    private int battleBuffMask = 0;
    private long lastBattleTick = -1L;
    private long lastBattleDayTime = -1L;
    private long lastDefenseDay = Long.MIN_VALUE;

    // Rewards survive chunk unloads and low-energy dawns. They are delivered as
    // soon as enough FE is available, exactly once.
    private boolean pendingReward = false;
    private int pendingThreatLevel = 0;
    private int pendingKills = 0;
    private int pendingStability = 0;
    private int pendingDoctrine = DOCTRINE_GUARD;
    private int pendingAffix = AFFIX_NONE;
    private int pendingScore = 0;
    private boolean pendingEnergyNoticeSent = false;
    private boolean pendingSpaceNoticeSent = false;
    @Nullable
    private UUID pendingRewardId;
    private boolean pendingEnergyReserved = false;
    private int pendingDeliveryRetryCooldown = 0;

    private int[] cachedTurretCounts = new int[3];
    private java.util.List<BlockPos> cachedNetworkNodes = java.util.List.of();
    private int turretScanCooldown = 0;
    private long lastTurretScanGameTime = Long.MIN_VALUE;

    /**
     * Players within notify range (50 blocks), refreshed on {@link #PLAYER_SCAN_INTERVAL}
     * rather than re-scanned on every warning / buff / status message. Beacon messages
     * are not frame-critical, so a ~1s staleness is fine. Server thread only; entries are
     * re-validated (alive / same level / in range) at use time so a logged-out or
     * teleported player can never be messaged through a stale reference.
     */
    private java.util.List<Player> cachedNearbyPlayers = java.util.List.of();
    private int playerScanCooldown = 0;
    private static final int PLAYER_SCAN_INTERVAL = 20;
    private static final int TURRET_SCAN_INTERVAL = 200;
    private static final int MIN_TURRET_SCAN_INTERVAL = 20;

    /**
     * Server-side registry of currently-active beacons, maintained each tick.
     * Lets mob-death / sleep lookups avoid scanning every block entity in a
     * large chunk radius. Server thread only.
     */
    private static final java.util.Set<PsychicBeaconBlockEntity> ACTIVE_BEACONS =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Find an active battle beacon within the radial {@code range} of {@code pos}. */
    @Nullable
    public static PsychicBeaconBlockEntity findNearbyActiveBeacon(Level level, BlockPos pos, int range) {
        PsychicBeaconBlockEntity found = null;
        java.util.Iterator<PsychicBeaconBlockEntity> it = ACTIVE_BEACONS.iterator();
        while (it.hasNext()) {
            PsychicBeaconBlockEntity beacon = it.next();
            // Prune stale entries opportunistically (unloaded / removed / wrong level).
            if (beacon.isRemoved() || beacon.level == null || !beacon.battleInProgress
                    || (beacon.beaconState != STATE_ACTIVE && beacon.beaconState != STATE_WARNING)) {
                it.remove();
                continue;
            }
            if (found == null && beacon.level == level
                    && beacon.worldPosition.distSqr(pos) <= (double) range * range) {
                found = beacon;
            }
        }
        return found;
    }

    /**
     * Drop every tracked beacon belonging to {@code level}. Hooked to level unload so a
     * dimension that goes away can't leave dangling block-entity references in the static
     * set until the next {@link #findNearbyActiveBeacon} call happens to prune them. A
     * beacon's {@code level} field is nulled on unload in some paths, so entries with a
     * null level are swept here too.
     */
    public static void clearBeaconsForLevel(Level level) {
        java.util.Iterator<PsychicBeaconBlockEntity> it = ACTIVE_BEACONS.iterator();
        while (it.hasNext()) {
            PsychicBeaconBlockEntity beacon = it.next();
            if (beacon.level == level || beacon.level == null || beacon.isRemoved()) {
                it.remove();
            }
        }
    }

    /** Drop all tracked beacons. Hooked to server stop so the static set never outlives a world. */
    public static void clearActiveBeacons() {
        ACTIVE_BEACONS.clear();
    }

    public static boolean hasActiveBattle(Level level) {
        java.util.Iterator<PsychicBeaconBlockEntity> it = ACTIVE_BEACONS.iterator();
        while (it.hasNext()) {
            PsychicBeaconBlockEntity beacon = it.next();
            if (beacon.isRemoved() || beacon.level == null || !beacon.battleInProgress
                    || (beacon.beaconState != STATE_ACTIVE && beacon.beaconState != STATE_WARNING)) {
                it.remove();
                continue;
            }
            if (beacon.level == level) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ACTIVE_BEACONS.remove(this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide) return;
        BlockPos upperPos = worldPosition.above();
        BlockState upperState = level.getBlockState(upperPos);
        if (upperState.is(ModRegistry.PSYCHIC_BEACON_BLOCK.get())) {
            level.updateNeighbourForOutputSignal(upperPos, upperState.getBlock());
        }
    }

    private final BeaconEnergyStorage energyStorage;
    private LazyOptional<IEnergyStorage> energyCap;

    public PsychicBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PSYCHIC_BEACON_BE.get(), pos, state);
        this.energyStorage = new BeaconEnergyStorage(TurretConfig.PSYCHIC_BEACON_CAPACITY.get(), MAX_RECEIVE) {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int received = super.receiveEnergy(maxReceive, simulate);
                if (received > 0 && !simulate) setChanged();
                return received;
            }
        };
        this.energyCap = LazyOptional.of(() -> this.energyStorage);
    }

    /**
     * The public FE capability is input-only, but beacon logic still needs an
     * internal atomic consume operation. Using EnergyStorage.extractEnergy with
     * maxExtract=0 made every configured cost a no-op.
     */
    private static class BeaconEnergyStorage extends EnergyStorage {
        BeaconEnergyStorage(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0, 0);
        }

        boolean consumeEnergy(int amount) {
            if (amount <= 0) return true;
            if (energy < amount) return false;
            energy -= amount;
            return true;
        }

        int receiveManualEnergy(int amount) {
            int received = Math.min(Math.max(0, amount), capacity - energy);
            energy += received;
            return received;
        }

        @Override
        public void deserializeNBT(Tag nbt) {
            super.deserializeNBT(nbt);
            energy = Math.max(0, Math.min(energy, capacity));
        }
    }

    public int getBeaconState() {
        return beaconState;
    }

    public int getStability() {
        return stability;
    }

    public int getThreatLevel() {
        return threatLevel;
    }

    public int getTodayKills() {
        return todayKills;
    }

    public static int getRequiredKillsForThreatLevel(int threatLevel) {
        int base = TurretConfig.PSYCHIC_BEACON_MIN_KILLS.get();
        return switch (Math.max(0, Math.min(4, threatLevel))) {
            case 0, 1 -> base;
            case 2 -> base + 3;
            case 3 -> base + 7;
            default -> base + 12;
        };
    }

    public int getSelectedBuffMask() {
        return selectedBuffMask;
    }

    public int getDoctrine() {
        return doctrine;
    }

    public int getActiveWaveAffix() {
        return activeWaveAffix;
    }

    public int getLastBattleScore() {
        return lastBattleScore;
    }

    public EnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    /** Manual redstone charging bypasses the external per-call FE input cap once. */
    public int receiveManualEnergy(int amount) {
        int received = energyStorage.receiveManualEnergy(amount);
        if (received > 0) setChanged();
        return received;
    }

    public static int getEffectiveDawnCost(int configuredCost, int capacity) {
        return Math.min(Math.max(0, configuredCost), Math.max(0, capacity));
    }

    public int getEffectiveDawnCost() {
        return getEffectiveDawnCost(
                TurretConfig.PSYCHIC_BEACON_DAWN_COST.get(),
                energyStorage.getMaxEnergyStored());
    }

    public boolean isVisuallyPowered() {
        return energyStorage.getEnergyStored() > 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isBattleInProgress() {
        return battleInProgress;
    }

    public void setOwner(Player player) {
        if (player == null || player instanceof FakePlayer) return;
        ownerUuid = player.getUUID();
        ownerName = player.getScoreboardName();
        setChanged();
    }

    public boolean canPlayerConfigure(Player player) {
        if (player == null || player instanceof FakePlayer) return false;
        if (ownerUuid == null) {
            if (!player.level().isClientSide) setOwner(player);
            return true;
        }
        if (ownerUuid.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        if (level == null) return false;

        Player onlineOwner = level.getPlayerByUUID(ownerUuid);
        if (onlineOwner != null && player.isAlliedTo(onlineOwner)) return true;
        if (!ownerName.isEmpty() && player.getTeam() != null) {
            net.minecraft.world.scores.PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
            return ownerTeam != null && ownerTeam == player.getTeam();
        }
        return false;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void incrementTodayKills() {
        if (!battleInProgress || !battleEligible || beaconState != STATE_ACTIVE) {
            return;
        }
        this.todayKills++;
        this.setChanged();
    }

    public int[] getCachedTurretCounts() {
        return cachedTurretCounts;
    }

    public java.util.List<BlockPos> getCachedNetworkNodes() {
        return cachedNetworkNodes;
    }

    public void cycleDoctrine() {
        if (battleInProgress) return;
        doctrine = (doctrine + 1) % DOCTRINE_COUNT;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void toggleSelectedBuff(int buffIndex) {
        if (battleInProgress) return;
        if (buffIndex < 0 || buffIndex >= BUFF_COUNT) return;
        if (!isBuffUnlocked(buffIndex, threatLevel)) return;

        int bit = 1 << buffIndex;
        if ((selectedBuffMask & bit) != 0) {
            selectedBuffMask &= ~bit;
        } else {
            int maxSelected = getMaxSelectedBuffs(threatLevel);
            if (Integer.bitCount(selectedBuffMask & getUnlockedBuffMask(threatLevel)) >= maxSelected) {
                return;
            }
            selectedBuffMask |= bit;
        }
        selectedBuffMask = normalizeSelectedBuffMask(selectedBuffMask, threatLevel);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static int getMaxSelectedBuffs(int threatLevel) {
        return Math.max(0, Math.min(3, threatLevel));
    }

    public static boolean isBuffUnlocked(int buffIndex, int threatLevel) {
        return switch (buffIndex) {
            case BUFF_SPEED, BUFF_HASTE -> threatLevel >= 1;
            case BUFF_RESISTANCE -> threatLevel >= 2;
            case BUFF_STRENGTH -> threatLevel >= 3;
            case BUFF_REGENERATION -> threatLevel >= 4;
            default -> false;
        };
    }

    public static int getUnlockedBuffMask(int threatLevel) {
        int mask = 0;
        for (int i = 0; i < BUFF_COUNT; i++) {
            if (isBuffUnlocked(i, threatLevel)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private static int normalizeSelectedBuffMask(int selectedMask, int threatLevel) {
        int allowed = getUnlockedBuffMask(threatLevel);
        int maxSelected = getMaxSelectedBuffs(threatLevel);
        int normalized = selectedMask & allowed;
        if (normalized == 0 && maxSelected > 0) {
            normalized = 1 << BUFF_SPEED;
        }
        while (Integer.bitCount(normalized) > maxSelected) {
            normalized &= normalized - 1;
        }
        return normalized;
    }

    private boolean refreshNearbyTurretCounts() {
        if (level == null) return false;
        long gameTime = level.getGameTime();
        if (lastTurretScanGameTime != Long.MIN_VALUE
                && gameTime >= lastTurretScanGameTime
                && gameTime - lastTurretScanGameTime < MIN_TURRET_SCAN_INTERVAL) {
            return false;
        }
        lastTurretScanGameTime = gameTime;

        int prism = 0;
        int tesla = 0;
        int gatling = 0;
        boolean collectNetworkNodes = beaconState == STATE_ACTIVE || beaconState == STATE_WARNING;
        java.util.ArrayList<BlockPos> nodes = new java.util.ArrayList<>();
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;

                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.getBlockPos().distManhattan(worldPosition) > 32) continue;

                    if (blockEntity instanceof PrismTowerBlockEntity) {
                        prism++;
                        if (collectNetworkNodes) addNetworkNode(nodes, blockEntity.getBlockPos());
                    } else if (blockEntity instanceof TeslaCoilBlockEntity) {
                        tesla++;
                        if (collectNetworkNodes) addNetworkNode(nodes, blockEntity.getBlockPos());
                    } else if (blockEntity instanceof GatlingTurretBlockEntity) {
                        gatling++;
                        if (collectNetworkNodes) addNetworkNode(nodes, blockEntity.getBlockPos());
                    }
                }
            }
        }

        java.util.List<BlockPos> refreshedNodes = java.util.List.copyOf(nodes);
        boolean changed = cachedTurretCounts[0] != prism
                || cachedTurretCounts[1] != tesla
                || cachedTurretCounts[2] != gatling
                || !cachedNetworkNodes.equals(refreshedNodes);
        cachedTurretCounts[0] = prism;
        cachedTurretCounts[1] = tesla;
        cachedTurretCounts[2] = gatling;
        cachedNetworkNodes = refreshedNodes;
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        return true;
    }

    private void clearCachedNetworkNodes() {
        if (cachedNetworkNodes.isEmpty()) return;
        cachedNetworkNodes = java.util.List.of();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private static void addNetworkNode(java.util.ArrayList<BlockPos> nodes, BlockPos pos) {
        if (nodes.size() < NETWORK_NODE_SYNC_CAP) {
            nodes.add(pos.immutable());
        }
    }

    public long getTimeUntilDawn() {
        if (level == null) return 0;
        long dayTime = level.getDayTime() % 24000;
        return dayTime < DAWN_TIME ? DAWN_TIME - dayTime : 0;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PsychicBeaconBlockEntity be) {
        if (level.isClientSide) {
            clientTick(level, pos, be);
            return;
        }

        int prevState = be.beaconState;

        if (!be.enabled && be.beaconState != STATE_OFFLINE) {
            if (be.beaconState == STATE_ACTIVE || be.beaconState == STATE_WARNING) {
                abortBattle(level, pos, be, false);
            }
            be.beaconState = STATE_OFFLINE;
        }

        // Migrate old saves that were already in an active night before battle
        // sessions were introduced.
        if ((be.beaconState == STATE_ACTIVE || be.beaconState == STATE_WARNING) && !be.battleInProgress) {
            be.restoreLegacyBattle(level);
        }

        // Unloading the beacon while the world keeps advancing must not be a free
        // way to skip the defense. Server shutdown is safe because gameTime does
        // not advance while the server is stopped.
        if (be.battleInProgress && be.hasBattleClockDiscontinuity(level)) {
            be.refreshNearbyPlayers(level);
            abortBattle(level, pos, be, true);
            be.beaconState = be.enabled && be.energyStorage.getEnergyStored() > 0
                    ? STATE_IDLE : STATE_OFFLINE;
        } else if (be.battleInProgress && be.lastBattleTick >= 0
                && level.getGameTime() - be.lastBattleTick > 200) {
            be.refreshNearbyPlayers(level);
            abortBattle(level, pos, be, true);
            be.beaconState = be.enabled && be.energyStorage.getEnergyStored() > 0
                    ? STATE_IDLE : STATE_OFFLINE;
        }

        if (be.battleInProgress || be.pendingReward) {
            be.playerScanCooldown--;
            if (be.playerScanCooldown <= 0) {
                be.refreshNearbyPlayers(level);
                be.playerScanCooldown = PLAYER_SCAN_INTERVAL;
            }
        } else if (!be.cachedNearbyPlayers.isEmpty()) {
            be.cachedNearbyPlayers = java.util.List.of();
            be.playerScanCooldown = 0;
        }

        switch (be.beaconState) {
            case STATE_OFFLINE:
                tickOffline(level, pos, be);
                break;
            case STATE_IDLE:
                tickIdle(level, pos, be);
                break;
            case STATE_ACTIVE:
                tickActive(level, pos, be);
                break;
            case STATE_WARNING:
                tickWarning(level, pos, be);
                break;
            case STATE_FAILED:
                break;
        }

        // Stability failure removes the block entity from inside tickActive.
        // Never run state synchronization against the stale pre-explosion state.
        if (be.isRemoved() || level.getBlockEntity(pos) != be) {
            ACTIVE_BEACONS.remove(be);
            return;
        }

        be.tryDeliverPendingReward();

        if (be.battleInProgress
                && (be.beaconState == STATE_ACTIVE || be.beaconState == STATE_WARNING)) {
            ACTIVE_BEACONS.add(be);
        } else {
            ACTIVE_BEACONS.remove(be);
        }

        if (be.beaconState == STATE_ACTIVE || be.beaconState == STATE_WARNING) {
            be.turretScanCooldown--;
            if (be.turretScanCooldown <= 0) {
                be.turretScanCooldown = be.refreshNearbyTurretCounts()
                        ? TURRET_SCAN_INTERVAL : MIN_TURRET_SCAN_INTERVAL;
            }
        } else {
            be.turretScanCooldown = 0;
            be.clearCachedNetworkNodes();
        }

        if (be.beaconState != prevState) {
            be.updateLitState(level, pos, state);
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }

        // Persist continuously-changing energy/timer/stability fields at a sane
        // cadence without sending full block-entity packets every tick.
        if (level.getGameTime() % 20 == 0
                && (be.beaconState == STATE_IDLE || be.beaconState == STATE_ACTIVE
                    || be.beaconState == STATE_WARNING || be.pendingReward)) {
            be.setChanged();
            be.lastBattleTick = be.battleInProgress ? level.getGameTime() : be.lastBattleTick;
            be.lastBattleDayTime = be.battleInProgress ? level.getDayTime() : be.lastBattleDayTime;
        }
    }

    private boolean hasBattleClockDiscontinuity(Level level) {
        return isBattleClockDiscontinuous(
                lastBattleTick, lastBattleDayTime, level.getGameTime(), level.getDayTime());
    }

    static boolean isBattleClockDiscontinuous(
            long previousGameTime, long previousDayTime, long gameTime, long dayTime) {
        if (previousGameTime < 0 || previousDayTime < 0) return true;
        long gameDelta = gameTime - previousGameTime;
        long dayDelta = dayTime - previousDayTime;
        return gameDelta < 0 || dayDelta < 0 || dayDelta > gameDelta + 5;
    }

    private void updateLitState(Level level, BlockPos pos, BlockState state) {
        boolean shouldBeLit = beaconState == STATE_IDLE || beaconState == STATE_ACTIVE || beaconState == STATE_WARNING;
        if (state.hasProperty(PsychicBeaconBlock.LIT) && state.getValue(PsychicBeaconBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(PsychicBeaconBlock.LIT, shouldBeLit), 3);
        }
        BlockPos upperPos = pos.above();
        BlockState upperState = level.getBlockState(upperPos);
        if (upperState.is(ModRegistry.PSYCHIC_BEACON_BLOCK.get())
                && upperState.hasProperty(PsychicBeaconBlock.LIT)
                && upperState.getValue(PsychicBeaconBlock.LIT) != shouldBeLit) {
            level.setBlock(upperPos, upperState.setValue(PsychicBeaconBlock.LIT, shouldBeLit), 3);
        }
    }

    private static void tickOffline(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.enabled && be.energyStorage.getEnergyStored() > 0 && !level.hasNeighborSignal(pos)) {
            be.beaconState = STATE_IDLE;
            be.scanCooldown = 0;
        }
    }

    private static void tickIdle(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.energyStorage.getEnergyStored() <= 0) {
            be.beaconState = STATE_OFFLINE;
            return;
        }

        if (level.hasNeighborSignal(pos)) {
            be.beaconState = STATE_OFFLINE;
            return;
        }

        int idleDrain = Math.min(TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get(), be.energyStorage.getEnergyStored());
        if (!be.energyStorage.consumeEnergy(idleDrain)) {
            be.beaconState = STATE_OFFLINE;
            return;
        }

        be.scanCooldown--;
        if (be.scanCooldown <= 0) {
            int previousThreat = be.threatLevel;
            int previousBuffMask = be.selectedBuffMask;
            be.threatLevel = be.scanPyramidLevel(level, pos);
            be.selectedBuffMask = normalizeSelectedBuffMask(be.selectedBuffMask, be.threatLevel);
            be.scanCooldown = 200;
            if (be.threatLevel != previousThreat || be.selectedBuffMask != previousBuffMask) {
                be.setChanged();
                level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 2);
            }
        }

        if (be.threatLevel > 0 && level.getGameTime() % 100 == 0) {
            broadcastBuffs(level, pos, be);
        }

        long dayTime = level.getDayTime() % 24000;
        if (!be.pendingReward && be.threatLevel > 0
                && be.lastDefenseDay != Math.floorDiv(level.getDayTime(), 24000L)
                && dayTime >= DEFENSE_START_TIME && dayTime < DAWN_TIME) {
            be.beaconState = STATE_ACTIVE;
            beginNightDefense(level, pos, be);
            be.spawnTimer = TurretConfig.PSYCHIC_BEACON_SPAWN_INTERVAL.get() / 2;
        }
    }

    private static void tickActive(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.energyStorage.getEnergyStored() <= 0) {
            abortBattle(level, pos, be, true);
            be.beaconState = STATE_OFFLINE;
            return;
        }

        if (level.hasNeighborSignal(pos)) {
            be.warningTimer = Math.max(0, be.warningTimer - 1);
            if (be.warningTimer <= 0) {
                emergencyShutdown(level, pos, be);
                return;
            }
            be.beaconState = STATE_WARNING;
            Player nearest = be.findNearestPlayer();
            if (nearest != null) {
                nearest.displayClientMessage(
                    Component.translatable("message.flux_turret.beacon_warning")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true
                );
            }
            return;
        }

        int activeDrain = Math.min(TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get(), be.energyStorage.getEnergyStored());
        if (!be.energyStorage.consumeEnergy(activeDrain)) {
            abortBattle(level, pos, be, true);
            be.beaconState = STATE_OFFLINE;
            return;
        }
        be.lastBattleTick = level.getGameTime();
        be.lastBattleDayTime = level.getDayTime();

        long dayTime = level.getDayTime() % 24000;
        if ((dayTime < DEFENSE_START_TIME || dayTime >= DAWN_TIME || level.getGameTime() % 200 == 0)
                && be.scanPyramidLevel(level, pos) < be.battleThreatLevel) {
            abortBattle(level, pos, be, true);
            be.beaconState = STATE_IDLE;
            return;
        }

        if (dayTime < DEFENSE_START_TIME || dayTime >= DAWN_TIME) {
            cleanupBeaconSpawnedMonsters(level, pos);
            completeNightDefense(be);
            be.beaconState = STATE_IDLE;
            be.activeWaveAffix = AFFIX_NONE;
            be.spawnTimer = 0;
            return;
        }

        if (level.getGameTime() % 20 == 0) {
            if (!tickStability(level, pos, be)) return;
        }

        if (be.battleThreatLevel > 0 && level.getGameTime() % 100 == 0) {
            broadcastBuffs(level, pos, be);
        }

        if (be.battleDoctrine == DOCTRINE_CONTROL && level.getGameTime() % 60 == 0) {
            applyControlDoctrine(level, pos, be);
        }

        be.spawnTimer++;
        if (be.spawnTimer >= TurretConfig.PSYCHIC_BEACON_SPAWN_INTERVAL.get()) {
            be.spawnTimer = 0;
            spawnWave(level, pos, be);
        }
    }

    private static void tickWarning(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        be.lastBattleTick = level.getGameTime();
        be.lastBattleDayTime = level.getDayTime();
        if (!level.hasNeighborSignal(pos)) {
            be.beaconState = STATE_ACTIVE;
            Player nearest = be.findNearestPlayer();
            if (nearest != null) {
                nearest.displayClientMessage(
                    Component.translatable("message.flux_turret.beacon_resumed")
                        .withStyle(net.minecraft.ChatFormatting.GREEN),
                    true
                );
            }
            return;
        }

        be.warningTimer--;
        if (be.warningTimer <= 0) {
            emergencyShutdown(level, pos, be);
        }
    }

    private static boolean tickStability(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        AABB checkArea = new AABB(pos).inflate(1.5);
        List<net.minecraft.world.entity.monster.Monster> nearbyMonsters = level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class, checkArea);
        int monsterCount = nearbyMonsters.size();
        if (monsterCount > 0) {
            int previous = be.stability;
            be.stability -= monsterCount;
            sendStabilityWarningIfNeeded(level, pos, be, previous);
            if (be.stability <= 0) {
                be.stability = 0;
                failAndExplode(level, pos, be);
                return false;
            }
        }
        return true;
    }

    private static void beginNightDefense(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.battleInProgress) return;
        be.battleInProgress = true;
        be.lastDefenseDay = Math.floorDiv(level.getDayTime(), 24000L);
        be.battleEligible = true;
        be.battleThreatLevel = Math.max(1, Math.min(4, be.threatLevel));
        be.battleDoctrine = Math.max(0, Math.min(DOCTRINE_COUNT - 1, be.doctrine));
        be.battleAffix = 1 + level.random.nextInt(AFFIX_COUNT - 1);
        be.battleBuffMask = normalizeSelectedBuffMask(be.selectedBuffMask, be.battleThreatLevel);
        be.activeWaveAffix = be.battleAffix;
        be.todayKills = 0;
        be.stability = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        be.stabilityNoticeStage = 0;
        be.warningTimer = 60;
        be.lastBattleTick = level.getGameTime();
        be.lastBattleDayTime = level.getDayTime();
        be.refreshNearbyPlayers(level);
        be.playerScanCooldown = PLAYER_SCAN_INTERVAL;
        be.setChanged();
        be.notifyNearbyPlayers(Component.translatable("message.flux_turret.beacon_affix",
                Component.translatable(getAffixTranslationKey(be.activeWaveAffix))).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }

    private void restoreLegacyBattle(Level level) {
        battleInProgress = true;
        lastDefenseDay = Math.floorDiv(level.getDayTime(), 24000L);
        battleEligible = true;
        battleThreatLevel = Math.max(1, Math.min(4, threatLevel));
        battleDoctrine = Math.max(0, Math.min(DOCTRINE_COUNT - 1, doctrine));
        battleAffix = activeWaveAffix == AFFIX_NONE
                ? 1 + level.random.nextInt(AFFIX_COUNT - 1)
                : activeWaveAffix;
        battleBuffMask = normalizeSelectedBuffMask(selectedBuffMask, battleThreatLevel);
        activeWaveAffix = battleAffix;
        if (warningTimer <= 0) warningTimer = 60;
        lastBattleTick = level.getGameTime();
        lastBattleDayTime = level.getDayTime();
        setChanged();
    }

    private static void abortBattle(Level level, BlockPos pos, PsychicBeaconBlockEntity be, boolean notify) {
        cleanupBeaconSpawnedMonsters(level, pos);
        boolean wasRunning = be.battleInProgress;
        be.battleInProgress = false;
        be.battleEligible = false;
        be.battleThreatLevel = 0;
        be.battleDoctrine = DOCTRINE_GUARD;
        be.battleAffix = AFFIX_NONE;
        be.battleBuffMask = 0;
        be.activeWaveAffix = AFFIX_NONE;
        be.todayKills = 0;
        be.spawnTimer = 0;
        be.warningTimer = 0;
        be.stability = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        be.stabilityNoticeStage = 0;
        be.lastBattleTick = -1L;
        be.lastBattleDayTime = -1L;
        be.setChanged();
        if (notify && wasRunning) {
            be.notifyNearbyPlayers(Component.translatable("message.flux_turret.beacon_defense_aborted")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    private static void completeNightDefense(PsychicBeaconBlockEntity be) {
        if (!be.battleInProgress) return;

        int threat = be.battleThreatLevel;
        int kills = be.todayKills;
        int remainingStability = Math.max(0, be.stability);
        int affix = be.battleAffix;
        int battleRoute = be.battleDoctrine;
        int requiredKills = getRequiredKillsForThreatLevel(threat);
        int score = calculateBattleScore(threat, kills, remainingStability, affix, battleRoute);
        be.lastBattleScore = score;

        if (be.battleEligible && kills >= requiredKills) {
            be.pendingReward = true;
            be.pendingThreatLevel = threat;
            be.pendingKills = kills;
            be.pendingStability = remainingStability;
            be.pendingDoctrine = battleRoute;
            be.pendingAffix = affix;
            be.pendingScore = score;
            be.pendingEnergyNoticeSent = false;
            be.pendingSpaceNoticeSent = false;
            be.pendingRewardId = UUID.randomUUID();
            be.pendingEnergyReserved = false;
            be.pendingDeliveryRetryCooldown = 0;
        } else if (be.level != null) {
            be.level.playSound(null, be.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);
            be.displayMessageToNearbyPlayers(
                    Component.translatable("message.flux_turret.beacon_defense_none", kills, requiredKills)
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }

        be.battleInProgress = false;
        be.battleEligible = false;
        be.battleThreatLevel = 0;
        be.battleDoctrine = DOCTRINE_GUARD;
        be.battleAffix = AFFIX_NONE;
        be.battleBuffMask = 0;
        be.lastBattleTick = -1L;
        be.lastBattleDayTime = -1L;
        be.todayKills = 0;
        be.activeWaveAffix = AFFIX_NONE;
        be.warningTimer = 0;
        be.stability = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        be.stabilityNoticeStage = 0;
        be.setChanged();
    }

    private static void applyControlDoctrine(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        int effectiveThreat = be.battleInProgress ? be.battleThreatLevel : be.threatLevel;
        int radius = 8 + Math.max(0, effectiveThreat) * 4;
        AABB area = new AABB(pos).inflate(radius);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, area, monster -> monster.isAlive() && !monster.isRemoved());
        for (Monster monster : monsters) {
            monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, effectiveThreat >= 3 ? 1 : 0, true, true));
            monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, true));
        }
    }

    private static void sendStabilityWarningIfNeeded(Level level, BlockPos pos, PsychicBeaconBlockEntity be, int previous) {
        int max = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        if (previous == be.stability) return;

        if (be.stabilityNoticeStage == 0) {
            be.stabilityNoticeStage = 1;
            be.notifyNearbyPlayers(Component.translatable("message.flux_turret.beacon_stability_drop", be.stability, max)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        if (be.stabilityNoticeStage < 2 && be.stability <= max / 2) {
            be.stabilityNoticeStage = 2;
            be.notifyNearbyPlayers(Component.translatable("message.flux_turret.beacon_stability_low", be.stability, max)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if (be.stabilityNoticeStage < 3 && be.stability <= Math.max(1, max / 4)) {
            be.stabilityNoticeStage = 3;
            be.notifyNearbyPlayers(Component.translatable("message.flux_turret.beacon_stability_critical", be.stability, max)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    /** Backwards-compatible public hook: now attempts delivery of an earned pending reward. */
    public void performDawnSynthesis() {
        tryDeliverPendingReward();
    }

    private void tryDeliverPendingReward() {
        if (!pendingReward || level == null || level.isClientSide) return;
        if (pendingRewardId == null) {
            pendingRewardId = UUID.randomUUID();
            setChanged();
        }
        if (findDeliveredRewardChest(level, worldPosition, pendingRewardId) != null) {
            clearPendingReward();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            return;
        }

        int cost = getEffectiveDawnCost();
        if (!pendingEnergyReserved && energyStorage.getEnergyStored() < cost) {
            if (!pendingEnergyNoticeSent) {
                boolean notified = displayMessageToNearbyPlayers(
                        Component.translatable("message.flux_turret.beacon_energy_low", cost)
                                .withStyle(net.minecraft.ChatFormatting.RED));
                if (notified) {
                    level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.BLOCKS, 1.0f, 0.5f);
                    pendingEnergyNoticeSent = true;
                    setChanged();
                }
            }
            return;
        }
        if (!pendingEnergyReserved) {
            if (!energyStorage.consumeEnergy(cost)) return;
            pendingEnergyReserved = true;
            setChanged();
        }

        if (pendingDeliveryRetryCooldown > 0) {
            pendingDeliveryRetryCooldown--;
            return;
        }

        BlockPos chestPos = findChestPos(level, worldPosition);
        if (chestPos == null) {
            pendingDeliveryRetryCooldown = PLAYER_SCAN_INTERVAL;
            if (!pendingSpaceNoticeSent) {
                boolean notified = displayMessageToNearbyPlayers(
                        Component.translatable("message.flux_turret.beacon_no_chest_space")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                if (notified) {
                    pendingSpaceNoticeSent = true;
                    setChanged();
                }
            }
            return;
        }

        if (!level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)) {
            pendingDeliveryRetryCooldown = PLAYER_SCAN_INTERVAL;
            return;
        }
        BlockEntity chestBe = level.getBlockEntity(chestPos);
        if (!(chestBe instanceof ChestBlockEntity chest)) {
            level.removeBlock(chestPos, false);
            pendingDeliveryRetryCooldown = PLAYER_SCAN_INTERVAL;
            return;
        }

        chest.getPersistentData().putUUID(REWARD_CHEST_ID_TAG, pendingRewardId);
        fillVictoryChestDynamic(level, worldPosition, chest, pendingThreatLevel, pendingKills,
                pendingScore, pendingDoctrine);
        chest.setChanged();

        level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0f, 1.0f);
        displayMessageToNearbyPlayers(
                Component.translatable("message.flux_turret.beacon_defense_success", pendingScore)
                        .withStyle(net.minecraft.ChatFormatting.AQUA));

        clearPendingReward();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    private void clearPendingReward() {
        pendingReward = false;
        pendingThreatLevel = 0;
        pendingKills = 0;
        pendingStability = 0;
        pendingDoctrine = DOCTRINE_GUARD;
        pendingAffix = AFFIX_NONE;
        pendingScore = 0;
        pendingEnergyNoticeSent = false;
        pendingSpaceNoticeSent = false;
        pendingRewardId = null;
        pendingEnergyReserved = false;
        pendingDeliveryRetryCooldown = 0;
    }

    private boolean displayMessageToNearbyPlayers(Component message) {
        return notifyNearbyPlayers(message);
    }

    /**
     * Refresh the notify-range player cache. One AABB scan per {@link #PLAYER_SCAN_INTERVAL}
     * ticks instead of one per status message. Server thread only.
     */
    private void refreshNearbyPlayers(Level level) {
        AABB area = new AABB(worldPosition).inflate(NOTIFY_RADIUS);
        cachedNearbyPlayers = level.getEntitiesOfClass(Player.class, area);
    }

    /**
     * Message every cached player still alive, in this level, and within notify range.
     * The range re-check matters because the cache is up to {@link #PLAYER_SCAN_INTERVAL}
     * ticks stale — a player may have walked out (or logged out) since the last scan.
     */
    private boolean notifyNearbyPlayers(Component message) {
        if (level == null) return false;
        double radiusSq = (double) NOTIFY_RADIUS * NOTIFY_RADIUS;
        boolean notified = false;
        for (int i = 0; i < cachedNearbyPlayers.size(); i++) {
            Player player = cachedNearbyPlayers.get(i);
            if (player == null || !player.isAlive() || player.level() != level) continue;
            if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) > radiusSq) continue;
            player.displayClientMessage(message, true);
            notified = true;
        }
        return notified;
    }

    private static void spawnWave(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        AABB countArea = new AABB(pos).inflate(MONSTER_COUNT_RADIUS);
        List<Monster> existingMonsters = level.getEntitiesOfClass(Monster.class, countArea);
        int eventMonsterCount = 0;
        for (Monster monster : existingMonsters) {
            if (isBeaconSpawn(monster, pos)) {
                eventMonsterCount++;
            }
        }
        int maxMonsters = TurretConfig.PSYCHIC_BEACON_MAX_MONSTERS.get()
                + (be.battleDoctrine == DOCTRINE_LURE ? 4 : 0)
                + (be.battleAffix == AFFIX_SWARM ? 3 : 0);
        int remaining = maxMonsters - eventMonsterCount;
        if (remaining <= 0) return;

        int tl = Math.max(1, Math.min(4, be.battleThreatLevel));
        RandomSource random = level.random;
        int waveBudget = Math.min(remaining, 2 + tl
                + (be.battleDoctrine == DOCTRINE_LURE ? 1 : 0)
                + (be.battleAffix == AFFIX_SWARM ? 2 : 0)
                + (be.battleAffix == AFFIX_RUSH || be.battleAffix == AFFIX_OVERLOAD ? 1 : 0));

        int huskCount = Math.min(waveBudget, 1 + random.nextInt(2) + (tl >= 3 ? 1 : 0)
                + (be.battleAffix == AFFIX_SWARM ? 1 : 0));
        waveBudget -= spawnHusks(level, pos, random, huskCount, tl, be.battleAffix);
        if (waveBudget <= 0) return;

        if (tl >= 1) {
            int spiderCount = Math.min(waveBudget, (tl >= 3 ? 2 : 1) + (be.battleAffix == AFFIX_RUSH ? 1 : 0));
            waveBudget -= spawnSpiders(level, pos, random, spiderCount, tl, be.battleAffix);
            if (waveBudget <= 0) return;
        }

        if (tl >= 3 && random.nextFloat() < (be.battleAffix == AFFIX_RUSH ? 0.85f : 0.65f)) {
            waveBudget -= spawnVexes(level, pos, random, Math.min(waveBudget, 1), tl, be.battleAffix);
            if (waveBudget <= 0) return;
        }

        if (tl >= 4 && random.nextFloat() < 0.35f) {
            waveBudget -= spawnChargedCreeper(level, pos, random);
            if (waveBudget <= 0) return;
        }

        if (be.stability < TurretConfig.PSYCHIC_BEACON_STABILITY.get() / 2 && random.nextFloat() < 0.5f) {
            spawnHusks(level, pos, random, Math.min(waveBudget, 1), tl, be.battleAffix);
        }
    }

    private static int spawnHusks(Level level, BlockPos pos, RandomSource random, int count, int threatLevel, int affix) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) continue;
            husk.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            applyEliteHealth(husk, getGroundEliteHealth(threatLevel, random) * getAffixHealthMultiplier(affix));
            applySpawnAffix(husk, affix);
            markBeaconSpawn(husk, pos);
            ensureMoveToBeaconGoal(husk, pos);
            level.addFreshEntity(husk);
            spawned++;
        }
        return spawned;
    }

    private static int spawnSpiders(Level level, BlockPos pos, RandomSource random, int count, int threatLevel, int affix) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Spider spider = EntityType.SPIDER.create(level);
            if (spider == null) continue;
            spider.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            applyEliteHealth(spider, getGroundEliteHealth(threatLevel, random) * getAffixHealthMultiplier(affix));
            applySpawnAffix(spider, affix);
            markBeaconSpawn(spider, pos);
            ensureMoveToBeaconGoal(spider, pos);
            level.addFreshEntity(spider);
            spawned++;
        }
        return spawned;
    }

    private static int spawnVexes(Level level, BlockPos pos, RandomSource random, int count, int threatLevel, int affix) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Vex vex = EntityType.VEX.create(level);
            if (vex == null) continue;
            vex.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            vex.setLimitedLife(2400);
            applyEliteHealth(vex, (threatLevel >= 4 ? 60.0F : 40.0F) * getAffixHealthMultiplier(affix));
            applySpawnAffix(vex, affix);
            markBeaconSpawn(vex, pos);
            ensureMoveToBeaconGoal(vex, pos);
            level.addFreshEntity(vex);
            spawned++;
        }
        return spawned;
    }

    private static int spawnChargedCreeper(Level level, BlockPos pos, RandomSource random) {
        BlockPos spawnPos = findSpawnPos(level, pos, random);
        if (spawnPos == null) return 0;
        Creeper creeper = EntityType.CREEPER.create(level);
        if (creeper == null) return 0;
        creeper.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
        creeper.getEntityData().set(Creeper.DATA_IS_POWERED, true);
        applyEliteHealth(creeper, 100.0F);
        markBeaconSpawn(creeper, pos);
        ensureMoveToBeaconGoal(creeper, pos);
        level.addFreshEntity(creeper);
        return 1;
    }

    private static float getGroundEliteHealth(int threatLevel, RandomSource random) {
        if (threatLevel >= 4 && random.nextFloat() < 0.30F) {
            return 100.0F;
        }
        if (threatLevel >= 2) {
            return 60.0F;
        }
        return 40.0F;
    }

    private static float getAffixHealthMultiplier(int affix) {
        return affix == AFFIX_ARMORED ? 1.45F : affix == AFFIX_OVERLOAD ? 1.20F : 1.0F;
    }

    private static void applySpawnAffix(Mob mob, int affix) {
        if (affix == AFFIX_RUSH) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1, true, true));
        } else if (affix == AFFIX_OVERLOAD) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 45, 0, true, true));
        }
    }

    private static void applyEliteHealth(Mob mob, float maxHealth) {
        AttributeInstance maxHealthAttribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
            mob.setHealth(maxHealth);
        }
    }

    private static void markBeaconSpawn(Mob mob, BlockPos beaconPos) {
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(BEACON_SPAWN_TAG, true);
        data.putLong(BEACON_POS_TAG, beaconPos.asLong());
    }

    @Nullable
    public static BlockPos getBeaconSpawnPos(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.getBoolean(BEACON_SPAWN_TAG) || !data.contains(BEACON_POS_TAG)) {
            return null;
        }
        return BlockPos.of(data.getLong(BEACON_POS_TAG));
    }

    public static void ensureMoveToBeaconGoal(Mob mob, BlockPos beaconPos) {
        boolean alreadyInstalled = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof MoveToBeaconGoal goal
                        && goal.getTargetPos().equals(beaconPos));
        if (!alreadyInstalled) {
            mob.goalSelector.addGoal(1, new MoveToBeaconGoal(mob, beaconPos, 1.0D));
        }
    }

    private static boolean isBeaconSpawn(Mob mob, BlockPos beaconPos) {
        BlockPos taggedPos = getBeaconSpawnPos(mob);
        return taggedPos != null && taggedPos.equals(beaconPos);
    }

    private static void cleanupBeaconSpawnedMonsters(Level level, BlockPos pos) {
        AABB clearArea = new AABB(pos).inflate(SPAWN_CLEANUP_RADIUS);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, clearArea);
        for (Monster monster : monsters) {
            if (isBeaconSpawn(monster, pos) || hasMoveToBeaconGoal(monster, pos)) {
                monster.discard();
            }
        }
    }

    private static boolean hasMoveToBeaconGoal(Monster monster, BlockPos beaconPos) {
        return monster.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof MoveToBeaconGoal goal
                        && goal.getTargetPos().equals(beaconPos));
    }

    @Nullable
    private static BlockPos findSpawnPos(Level level, BlockPos beaconPos, RandomSource random) {
        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 15 + random.nextDouble() * 10;
            int x = beaconPos.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = beaconPos.getZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos columnPos = new BlockPos(x, beaconPos.getY(), z);
            if (!level.hasChunkAt(columnPos)) continue;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())) {
                return candidate;
            }
        }
        return null;
    }

    private static void broadcastBuffs(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        int effectiveThreat = be.battleInProgress ? be.battleThreatLevel : be.threatLevel;
        int effectiveDoctrine = be.battleInProgress ? be.battleDoctrine : be.doctrine;
        int selected = be.battleInProgress
                ? be.battleBuffMask
                : normalizeSelectedBuffMask(be.selectedBuffMask, effectiveThreat);
        int radius = (effectiveThreat + 1) * 10 + (effectiveDoctrine == DOCTRINE_GUARD ? 10 : 0);
        if (!be.battleInProgress && selected != be.selectedBuffMask) {
            be.selectedBuffMask = selected;
            be.setChanged();
        }
        if (selected == 0) return;

        AABB area = new AABB(pos).inflate(radius);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        for (Player player : players) {
            applySelectedBuffs(player, selected, effectiveThreat, effectiveDoctrine);
        }
    }

    private static void applySelectedBuffs(Player player, int selected, int threatLevel, int doctrine) {
        int amplifier = getBuffAmplifier(threatLevel) + (doctrine == DOCTRINE_GUARD && threatLevel >= 2 ? 1 : 0);
        if ((selected & (1 << BUFF_SPEED)) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, amplifier, true, true));
        }
        if ((selected & (1 << BUFF_HASTE)) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, amplifier, true, true));
        }
        if ((selected & (1 << BUFF_RESISTANCE)) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 260, Math.min(1, amplifier), true, true));
        }
        if ((selected & (1 << BUFF_STRENGTH)) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 260, amplifier, true, true));
        }
        if ((selected & (1 << BUFF_REGENERATION)) != 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 260, Math.min(1, amplifier), true, true));
        }
    }

    private static int getBuffAmplifier(int threatLevel) {
        if (threatLevel >= 4) {
            return 2;
        }
        if (threatLevel >= 3) {
            return 1;
        }
        return 0;
    }

    public static String getDoctrineTranslationKey(int doctrine) {
        return switch (doctrine) {
            case DOCTRINE_LURE -> "doctrine.flux_turret.lure";
            case DOCTRINE_CONTROL -> "doctrine.flux_turret.control";
            default -> "doctrine.flux_turret.guard";
        };
    }

    public static String getAffixTranslationKey(int affix) {
        return switch (affix) {
            case AFFIX_ARMORED -> "affix.flux_turret.armored";
            case AFFIX_SWARM -> "affix.flux_turret.swarm";
            case AFFIX_RUSH -> "affix.flux_turret.rush";
            case AFFIX_OVERLOAD -> "affix.flux_turret.overload";
            default -> "affix.flux_turret.none";
        };
    }

    private static void failAndExplode(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        abortBattle(level, pos, be, false);
        be.clearPendingReward();
        be.beaconState = STATE_FAILED;
        be.notifyNearbyPlayers(Component.translatable("message.flux_turret.beacon_stability_failure")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED));

        // Use BLOCK interaction mode to respect explosion protection
        level.explode(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 5.0f, Level.ExplosionInteraction.BLOCK);

        RandomSource random = level.random;
        if (random.nextFloat() < 0.5f) {
            ItemStack scrap = new ItemStack(Items.AMETHYST_SHARD, 2 + random.nextInt(3));
            net.minecraft.world.entity.item.ItemEntity scrapEntity = new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, scrap);
            level.addFreshEntity(scrapEntity);
        }
        if (random.nextFloat() < 0.1f) {
            ItemStack skull = new ItemStack(Items.WITHER_SKELETON_SKULL);
            net.minecraft.world.entity.item.ItemEntity skullEntity = new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, skull);
            level.addFreshEntity(skullEntity);
        }

        net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        cloud.setRadius(15.0F);
        cloud.setDuration(24000);
        cloud.setParticle(ParticleTypes.PORTAL);
        cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        level.addFreshEntity(cloud);

        if (level.getBlockState(pos.above()).is(ModRegistry.PSYCHIC_BEACON_BLOCK.get())) {
            level.removeBlock(pos.above(), false);
        }
        level.removeBlock(pos, false);
    }

    private static void emergencyShutdown(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        abortBattle(level, pos, be, true);
        int cost = be.getEffectiveDawnCost();
        be.energyStorage.consumeEnergy(Math.min(cost, be.energyStorage.getEnergyStored()));

        be.beaconState = STATE_OFFLINE;
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
    }

    @Nullable
    private static BlockPos findDeliveredRewardChest(Level level, BlockPos beaconPos, UUID rewardId) {
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = beaconPos.offset(dx, dy, dz);
                    if (!isInSameChunk(beaconPos, candidate)) continue;
                    BlockEntity blockEntity = level.getBlockEntity(candidate);
                    if (blockEntity instanceof ChestBlockEntity chest
                            && chest.getPersistentData().hasUUID(REWARD_CHEST_ID_TAG)
                            && rewardId.equals(chest.getPersistentData().getUUID(REWARD_CHEST_ID_TAG))) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findChestPos(Level level, BlockPos pos) {
        // First pass: air with a solid block beneath (a "nice" chest spot) around the beacon.
        for (int dy = 1; dy >= -1; dy--) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = pos.relative(dir).above(dy);
                if (isInSameChunk(pos, candidate)
                        && canPlaceChestAt(level, candidate)
                        && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())) {
                    return candidate;
                }
            }
        }
        // Second pass: any replaceable/air spot in the immediate 3x3x3 shell, supported or not.
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos candidate = pos.offset(dx, dy, dz);
                    if (isInSameChunk(pos, candidate) && canPlaceChestAt(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isInSameChunk(BlockPos first, BlockPos second) {
        return (first.getX() >> 4) == (second.getX() >> 4)
                && (first.getZ() >> 4) == (second.getZ() >> 4);
    }

    private static boolean canPlaceChestAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private static void fillVictoryChestDynamic(Level level, BlockPos beaconPos, Container chest,
            int threatLevel, int todayKills, int battleScore, int doctrine) {
        RandomSource random = level.random;

        int baseSlots = Math.min(1 + todayKills / 5, 5);
        int bonusSlots = threatLevel >= 3 ? 3 : threatLevel >= 2 ? 2 : 1;
        bonusSlots += battleScore >= 450 ? 2 : battleScore >= 300 ? 1 : 0;
        if (doctrine == DOCTRINE_LURE) {
            bonusSlots += 1;
        }
        int totalFillSlots = Math.min(baseSlots + bonusSlots, chest.getContainerSize());

        for (int i = 0; i < totalFillSlots && i < 3; i++) {
            insertReward(chest, new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(5)));
        }

        if (totalFillSlots > 3) {
            insertReward(chest, new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4)));
        }

        if (threatLevel >= 1 && totalFillSlots > 4) {
            insertReward(chest, new ItemStack(ModRegistry.ENERGY_CRYSTAL_ITEM.get()));
            if (random.nextBoolean()) {
                insertReward(chest, new ItemStack(ModRegistry.ENERGY_CRYSTAL_ITEM.get()));
            }
        }

        if (threatLevel >= 2 && totalFillSlots > 5) {
            insertReward(chest, new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
        }

        if (threatLevel >= 3 && level instanceof ServerLevel serverLevel) {
            LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(
                    net.minecraft.world.level.storage.loot.BuiltInLootTables.END_CITY_TREASURE);
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(beaconPos))
                    .create(LootContextParamSets.CHEST);
            List<ItemStack> rewards = lootTable.getRandomItems(params);
            for (ItemStack reward : rewards) {
                if (!insertReward(chest, reward.copy())) break;
            }
        }

        if (threatLevel >= 4 && random.nextFloat() < 0.15f) {
            insertReward(chest, new ItemStack(Items.NETHER_STAR));
        }

        if (battleScore >= 260) {
            insertReward(chest, randomUpgradeModule(random));
        }
        if (battleScore >= 520) {
            insertReward(chest, randomUpgradeModule(random));
        }
        if (battleScore >= 650 && random.nextFloat() < 0.35f) {
            insertReward(chest, new ItemStack(ModRegistry.EMPOWERED_ENERGY_CRYSTAL_ITEM.get()));
        }
    }

    private static boolean insertReward(Container container, ItemStack stack) {
        if (stack.isEmpty()) return true;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    stack.shrink(move);
                    container.setChanged();
                    if (stack.isEmpty()) return true;
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) continue;
            int move = Math.min(stack.getCount(), stack.getMaxStackSize());
            container.setItem(slot, stack.split(move));
            if (stack.isEmpty()) return true;
        }
        return false;
    }

    private static int calculateBattleScore(int threatLevel, int kills, int stability, int affix, int doctrine) {
        int score = threatLevel * 90 + kills * 24 + Math.max(0, stability) * 2;
        if (affix != AFFIX_NONE) {
            score += 60;
        }
        if (doctrine == DOCTRINE_LURE) {
            score += 45;
        } else if (doctrine == DOCTRINE_CONTROL) {
            score += 25;
        }
        return Math.max(0, score);
    }

    private static ItemStack randomUpgradeModule(RandomSource random) {
        return switch (random.nextInt(12)) {
            case 0 -> new ItemStack(ModRegistry.ARMOR_PIERCING_ROUNDS_MODULE.get());
            case 1 -> new ItemStack(ModRegistry.FIRE_ROUNDS_MODULE.get());
            case 2 -> new ItemStack(ModRegistry.SLOW_ROUNDS_MODULE.get());
            case 3 -> new ItemStack(ModRegistry.CHAIN_JUMP_MODULE.get());
            case 4 -> new ItemStack(ModRegistry.EMP_SLOW_MODULE.get());
            case 5 -> new ItemStack(ModRegistry.OVERLOAD_BURST_MODULE.get());
            case 6 -> new ItemStack(ModRegistry.FOCUSED_BEAM_MODULE.get());
            case 7 -> new ItemStack(ModRegistry.REFRACTION_BEAM_MODULE.get());
            case 8 -> new ItemStack(ModRegistry.REMOTE_SUPPORT_MODULE.get());
            case 9 -> new ItemStack(ModRegistry.SEISMIC_SHOCK_MODULE.get());
            case 10 -> new ItemStack(ModRegistry.ARMOR_BREAK_MODULE.get());
            default -> new ItemStack(ModRegistry.CLUSTER_SHELLS_MODULE.get());
        };
    }

    /**
     * Nearest cached player still alive, in this level, and within notify range.
     * Reads the {@link #cachedNearbyPlayers} snapshot rather than scanning the world;
     * the range/liveness re-check covers cache staleness (see {@link #notifyNearbyPlayers}).
     */
    @Nullable
    private Player findNearestPlayer() {
        if (level == null) return null;
        double radiusSq = (double) NOTIFY_RADIUS * NOTIFY_RADIUS;
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < cachedNearbyPlayers.size(); i++) {
            Player p = cachedNearbyPlayers.get(i);
            if (p == null || !p.isAlive() || p.level() != level) continue;
            double dist = p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
            if (dist > radiusSq) continue;
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    public int scanPyramidLevel(Level level, BlockPos pos) {
        if (!checkLayer(level, pos.below(), 1)) return 0;
        if (!checkLayer(level, pos.below(2), 2)) return 1;
        if (!checkLayer(level, pos.below(3), 3)) return 2;
        if (!checkLayer(level, pos.below(4), 4)) return 3;
        return 4;
    }

    private boolean checkLayer(Level level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                net.minecraft.world.level.block.Block block = level.getBlockState(center.offset(x, 0, z)).getBlock();
                if (block != Blocks.IRON_BLOCK
                        && block != Blocks.GOLD_BLOCK
                        && block != Blocks.DIAMOND_BLOCK
                        && block != Blocks.EMERALD_BLOCK
                        && block != Blocks.NETHERITE_BLOCK) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void clientTick(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.beaconState == STATE_WARNING) {
            if (level.random.nextInt(3) == 0) {
                double dx = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                double dy = pos.getY() + 1.5 + level.random.nextDouble() * 0.5;
                double dz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
                level.addParticle(ParticleTypes.ANGRY_VILLAGER, dx, dy, dz, 0, 0.05, 0);
            }
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> this.energyStorage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Energy", energyStorage.serializeNBT());
        tag.putInt("BeaconState", beaconState);
        tag.putInt("Stability", stability);
        tag.putInt("ThreatLevel", threatLevel);
        tag.putInt("SpawnTimer", spawnTimer);
        tag.putInt("WarningTimer", warningTimer);
        tag.putInt("TodayKills", todayKills);
        tag.putBoolean("DawnProcessed", dawnProcessed);
        tag.putBoolean("Enabled", enabled);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        if (!ownerName.isEmpty()) tag.putString("OwnerName", ownerName);
        tag.putInt("SelectedBuffMask", selectedBuffMask);
        tag.putInt("StabilityNoticeStage", stabilityNoticeStage);
        tag.putInt("Doctrine", doctrine);
        tag.putInt("ActiveWaveAffix", activeWaveAffix);
        tag.putInt("LastBattleScore", lastBattleScore);
        tag.putBoolean("BattleInProgress", battleInProgress);
        tag.putBoolean("BattleEligible", battleEligible);
        tag.putInt("BattleThreatLevel", battleThreatLevel);
        tag.putInt("BattleDoctrine", battleDoctrine);
        tag.putInt("BattleAffix", battleAffix);
        tag.putInt("BattleBuffMask", battleBuffMask);
        tag.putLong("LastBattleTick", lastBattleTick);
        tag.putLong("LastBattleDayTime", lastBattleDayTime);
        tag.putLong("LastDefenseDay", lastDefenseDay);
        tag.putBoolean("PendingReward", pendingReward);
        tag.putInt("PendingThreatLevel", pendingThreatLevel);
        tag.putInt("PendingKills", pendingKills);
        tag.putInt("PendingStability", pendingStability);
        tag.putInt("PendingDoctrine", pendingDoctrine);
        tag.putInt("PendingAffix", pendingAffix);
        tag.putInt("PendingScore", pendingScore);
        tag.putBoolean("PendingEnergyNoticeSent", pendingEnergyNoticeSent);
        tag.putBoolean("PendingSpaceNoticeSent", pendingSpaceNoticeSent);
        if (pendingRewardId != null) tag.putUUID("PendingRewardId", pendingRewardId);
        tag.putBoolean("PendingEnergyReserved", pendingEnergyReserved);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Energy")) {
            energyStorage.deserializeNBT(tag.get("Energy"));
        }
        beaconState = tag.getInt("BeaconState");
        stability = tag.getInt("Stability");
        threatLevel = tag.getInt("ThreatLevel");
        spawnTimer = tag.getInt("SpawnTimer");
        warningTimer = tag.getInt("WarningTimer");
        todayKills = tag.getInt("TodayKills");
        dawnProcessed = tag.getBoolean("DawnProcessed");
        enabled = tag.contains("Enabled") ? tag.getBoolean("Enabled") : true;
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.contains("OwnerName", Tag.TAG_STRING) ? tag.getString("OwnerName") : "";
        selectedBuffMask = tag.contains("SelectedBuffMask") ? tag.getInt("SelectedBuffMask") : (1 << BUFF_SPEED);
        stabilityNoticeStage = tag.getInt("StabilityNoticeStage");
        doctrine = tag.contains("Doctrine") ? Math.max(0, Math.min(DOCTRINE_COUNT - 1, tag.getInt("Doctrine"))) : DOCTRINE_GUARD;
        activeWaveAffix = tag.contains("ActiveWaveAffix") ? Math.max(0, Math.min(AFFIX_COUNT - 1, tag.getInt("ActiveWaveAffix"))) : AFFIX_NONE;
        lastBattleScore = tag.getInt("LastBattleScore");
        battleInProgress = tag.getBoolean("BattleInProgress");
        if (battleInProgress && warningTimer <= 0) warningTimer = 60;
        battleEligible = tag.contains("BattleEligible") ? tag.getBoolean("BattleEligible") : battleInProgress;
        battleThreatLevel = battleInProgress ? Math.max(1, Math.min(4, tag.getInt("BattleThreatLevel"))) : 0;
        battleDoctrine = battleInProgress
                ? Math.max(0, Math.min(DOCTRINE_COUNT - 1, tag.getInt("BattleDoctrine")))
                : DOCTRINE_GUARD;
        battleAffix = battleInProgress
                ? Math.max(AFFIX_ARMORED, Math.min(AFFIX_COUNT - 1, tag.getInt("BattleAffix")))
                : AFFIX_NONE;
        battleBuffMask = battleInProgress
                ? normalizeSelectedBuffMask(tag.contains("BattleBuffMask")
                        ? tag.getInt("BattleBuffMask") : selectedBuffMask, battleThreatLevel)
                : 0;
        lastBattleTick = tag.contains("LastBattleTick", Tag.TAG_ANY_NUMERIC) ? tag.getLong("LastBattleTick") : -1L;
        lastBattleDayTime = tag.contains("LastBattleDayTime", Tag.TAG_ANY_NUMERIC)
                ? tag.getLong("LastBattleDayTime") : -1L;
        lastDefenseDay = tag.contains("LastDefenseDay", Tag.TAG_ANY_NUMERIC)
                ? tag.getLong("LastDefenseDay") : Long.MIN_VALUE;

        pendingReward = tag.getBoolean("PendingReward");
        pendingThreatLevel = pendingReward ? Math.max(1, Math.min(4, tag.getInt("PendingThreatLevel"))) : 0;
        pendingKills = pendingReward ? Math.max(0, tag.getInt("PendingKills")) : 0;
        pendingStability = pendingReward ? Math.max(0, tag.getInt("PendingStability")) : 0;
        pendingDoctrine = pendingReward
                ? Math.max(0, Math.min(DOCTRINE_COUNT - 1, tag.getInt("PendingDoctrine")))
                : DOCTRINE_GUARD;
        pendingAffix = pendingReward
                ? Math.max(AFFIX_NONE, Math.min(AFFIX_COUNT - 1, tag.getInt("PendingAffix")))
                : AFFIX_NONE;
        pendingScore = pendingReward ? Math.max(0, tag.getInt("PendingScore")) : 0;
        pendingEnergyNoticeSent = pendingReward && tag.getBoolean("PendingEnergyNoticeSent");
        pendingSpaceNoticeSent = pendingReward && tag.getBoolean("PendingSpaceNoticeSent");
        pendingRewardId = pendingReward && tag.hasUUID("PendingRewardId")
                ? tag.getUUID("PendingRewardId") : null;
        pendingEnergyReserved = pendingReward && tag.getBoolean("PendingEnergyReserved");
        pendingDeliveryRetryCooldown = 0;
        if (tag.contains("NetworkNodes")) {
            long[] networkNodes = tag.getLongArray("NetworkNodes");
            java.util.ArrayList<BlockPos> nodes = new java.util.ArrayList<>();
            for (long node : networkNodes) {
                if (nodes.size() >= NETWORK_NODE_SYNC_CAP) break;
                nodes.add(BlockPos.of(node));
            }
            cachedNetworkNodes = java.util.List.copyOf(nodes);
        } else {
            cachedNetworkNodes = java.util.List.of();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        long[] networkNodes = new long[cachedNetworkNodes.size()];
        for (int i = 0; i < cachedNetworkNodes.size(); i++) {
            networkNodes[i] = cachedNetworkNodes.get(i).asLong();
        }
        tag.putLongArray("NetworkNodes", networkNodes);
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
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            switch (this.beaconState) {
                case STATE_OFFLINE:
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.psychic_beacon.offline"));
                case STATE_IDLE:
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.psychic_beacon.idle"));
                case STATE_ACTIVE:
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.psychic_beacon.active"));
                case STATE_FAILED:
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.psychic_beacon.fail"));
                case STATE_WARNING:
                    return state.setAndContinue(RawAnimation.begin().thenLoop("animation.psychic_beacon.active"));
                default:
                    return PlayState.STOP;
            }
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0D, 192.0D, 2.0D);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.flux_turret.psychic_beacon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        refreshNearbyTurretCounts();
        return new PsychicBeaconMenu(containerId, playerInventory, this);
    }
}
