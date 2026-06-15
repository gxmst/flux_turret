package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.PsychicBeaconBlock;
import com.mymod.flux_turret.menu.PsychicBeaconMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

public class PsychicBeaconBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final int MAX_RECEIVE = 1000;
    private static final String BEACON_SPAWN_TAG = "FluxTurretBeaconSpawn";
    private static final String BEACON_POS_TAG = "FluxTurretBeaconPos";
    private static final int MONSTER_COUNT_RADIUS = 32;
    private static final int SPAWN_CLEANUP_RADIUS = 48;

    // Constants for better code readability
    private static final int SLEEP_PREVENTION_RADIUS = 100;
    private static final int DEATH_DETECTION_RADIUS = 32;

    public static final int STATE_OFFLINE = 0;
    public static final int STATE_IDLE = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_FAILED = 3;
    public static final int STATE_WARNING = 4;

    private int beaconState = STATE_OFFLINE;
    private int stability = 100;
    private int threatLevel = 0;
    private int spawnTimer = 0;
    private int warningTimer = 0;
    private int scanCooldown = 0;
    private int todayKills = 0;
    private boolean dawnProcessed = false;
    private boolean enabled = true;

    private int[] cachedTurretCounts = new int[3];
    private int turretScanCooldown = 0;

    /**
     * Server-side registry of currently-active beacons, maintained each tick.
     * Lets mob-death / sleep lookups avoid scanning every block entity in a
     * large chunk radius. Server thread only.
     */
    private static final java.util.Set<PsychicBeaconBlockEntity> ACTIVE_BEACONS =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Find an active beacon within {@code range} (Manhattan) of {@code pos} in the given level. */
    @Nullable
    public static PsychicBeaconBlockEntity findNearbyActiveBeacon(Level level, BlockPos pos, int range) {
        PsychicBeaconBlockEntity found = null;
        java.util.Iterator<PsychicBeaconBlockEntity> it = ACTIVE_BEACONS.iterator();
        while (it.hasNext()) {
            PsychicBeaconBlockEntity beacon = it.next();
            // Prune stale entries opportunistically (unloaded / removed / wrong level).
            if (beacon.isRemoved() || beacon.level == null || beacon.beaconState != STATE_ACTIVE) {
                it.remove();
                continue;
            }
            if (found == null && beacon.level == level
                    && beacon.worldPosition.distManhattan(pos) <= range) {
                found = beacon;
            }
        }
        return found;
    }

    /** Drop all tracked beacons. Hooked to server stop so the static set never outlives a world. */
    public static void clearActiveBeacons() {
        ACTIVE_BEACONS.clear();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ACTIVE_BEACONS.remove(this);
    }

    private final EnergyStorage energyStorage;
    private LazyOptional<IEnergyStorage> energyCap;

    public PsychicBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PSYCHIC_BEACON_BE.get(), pos, state);
        this.energyStorage = new EnergyStorage(TurretConfig.PSYCHIC_BEACON_CAPACITY.get(), MAX_RECEIVE, 0, 0) {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int received = super.receiveEnergy(maxReceive, simulate);
                if (received > 0 && !simulate) setChanged();
                return received;
            }
        };
        this.energyCap = LazyOptional.of(() -> this.energyStorage);
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

    public EnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public boolean isVisuallyPowered() {
        return energyStorage.getEnergyStored() > 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void incrementTodayKills() {
        this.todayKills++;
        this.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int[] getCachedTurretCounts() {
        return cachedTurretCounts;
    }

    private void refreshNearbyTurretCounts() {
        if (level == null) return;

        int prism = 0;
        int tesla = 0;
        int gatling = 0;
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
                    } else if (blockEntity instanceof TeslaCoilBlockEntity) {
                        tesla++;
                    } else if (blockEntity instanceof GatlingTurretBlockEntity) {
                        gatling++;
                    }
                }
            }
        }

        cachedTurretCounts[0] = prism;
        cachedTurretCounts[1] = tesla;
        cachedTurretCounts[2] = gatling;
    }

    public long getTimeUntilDawn() {
        if (level == null) return 0;
        long dayTime = level.getDayTime() % 24000;
        if (dayTime <= 6000) {
            return 6000 - dayTime;
        } else {
            return 24000 - dayTime + 6000;
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PsychicBeaconBlockEntity be) {
        if (level.isClientSide) {
            clientTick(level, pos, be);
            return;
        }

        int prevState = be.beaconState;

        if (!be.enabled && be.beaconState != STATE_OFFLINE) {
            if (be.beaconState == STATE_ACTIVE || be.beaconState == STATE_WARNING) {
                cleanupBeaconSpawnedMonsters(level, pos);
            }
            be.beaconState = STATE_OFFLINE;
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

        tickDawnSynthesis(level, pos, be);

        if (be.beaconState == STATE_ACTIVE) {
            ACTIVE_BEACONS.add(be);
        } else {
            ACTIVE_BEACONS.remove(be);
        }

        be.turretScanCooldown--;
        if (be.turretScanCooldown <= 0) {
            be.refreshNearbyTurretCounts();
            be.turretScanCooldown = 20;
        }

        if (be.beaconState != prevState) {
            be.updateLitState(level, pos, state);
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void updateLitState(Level level, BlockPos pos, BlockState state) {
        boolean shouldBeLit = beaconState == STATE_IDLE || beaconState == STATE_ACTIVE || beaconState == STATE_WARNING;
        if (state.hasProperty(PsychicBeaconBlock.LIT) && state.getValue(PsychicBeaconBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(PsychicBeaconBlock.LIT, shouldBeLit), 3);
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

        be.energyStorage.extractEnergy(Math.min(TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get(), be.energyStorage.getEnergyStored()), false);

        be.scanCooldown--;
        if (be.scanCooldown <= 0) {
            be.threatLevel = be.scanPyramidLevel(level, pos);
            be.scanCooldown = 100;
        }

        if (be.threatLevel > 0 && level.getGameTime() % 100 == 0) {
            broadcastBuffs(level, pos, be);
        }

        long dayTime = level.getDayTime() % 24000;
        if (dayTime >= 13000 && dayTime < 23000) {
            be.beaconState = STATE_ACTIVE;
            be.spawnTimer = TurretConfig.PSYCHIC_BEACON_SPAWN_INTERVAL.get() / 2;
        }
    }

    private static void tickActive(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.energyStorage.getEnergyStored() <= 0) {
            cleanupBeaconSpawnedMonsters(level, pos);
            be.beaconState = STATE_OFFLINE;
            return;
        }

        if (level.hasNeighborSignal(pos)) {
            be.beaconState = STATE_WARNING;
            be.warningTimer = 60;
            Player nearest = findNearestPlayer(level, pos, 50);
            if (nearest != null) {
                nearest.displayClientMessage(
                    Component.translatable("message.flux_turret.beacon_warning")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true
                );
            }
            return;
        }

        be.energyStorage.extractEnergy(Math.min(TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get(), be.energyStorage.getEnergyStored()), false);

        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 13000 || dayTime >= 23000) {
            cleanupBeaconSpawnedMonsters(level, pos);
            be.beaconState = STATE_IDLE;
            be.spawnTimer = 0;
            return;
        }

        if (level.getGameTime() % 20 == 0) {
            tickStability(level, pos, be);
        }

        be.spawnTimer++;
        if (be.spawnTimer >= TurretConfig.PSYCHIC_BEACON_SPAWN_INTERVAL.get()) {
            be.spawnTimer = 0;
            spawnWave(level, pos, be);
        }
    }

    private static void tickWarning(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (!level.hasNeighborSignal(pos)) {
            be.beaconState = STATE_ACTIVE;
            be.warningTimer = 0;
            Player nearest = findNearestPlayer(level, pos, 50);
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

    private static void tickStability(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        AABB checkArea = new AABB(pos).inflate(1.5);
        List<net.minecraft.world.entity.monster.Monster> nearbyMonsters = level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class, checkArea);
        int monsterCount = nearbyMonsters.size();
        if (monsterCount > 0) {
            be.stability -= monsterCount;
            if (be.stability <= 0) {
                be.stability = 0;
                failAndExplode(level, pos, be);
            }
        }
    }

    private static void tickDawnSynthesis(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        long dayTime = level.getDayTime() % 24000;
        if (!be.dawnProcessed && dayTime >= 6000 && dayTime < 6100) {
            be.performDawnSynthesis();
            be.dawnProcessed = true;
            be.setChanged();
        }
        if (dayTime >= 6100 || dayTime < 6000) {
            be.dawnProcessed = false;
        }
    }

    public void performDawnSynthesis() {
        if (this.todayKills < TurretConfig.PSYCHIC_BEACON_MIN_KILLS.get()) {
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);
                displayMessageToNearbyPlayers(
                    Component.translatable("message.flux_turret.beacon_defense_none")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW)
                );
            }
            this.todayKills = 0;
            setChanged();
            return;
        }

        if (this.energyStorage.getEnergyStored() < TurretConfig.PSYCHIC_BEACON_DAWN_COST.get()) {
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.5f);
                displayMessageToNearbyPlayers(
                    Component.translatable("message.flux_turret.beacon_energy_low", TurretConfig.PSYCHIC_BEACON_DAWN_COST.get())
                        .withStyle(net.minecraft.ChatFormatting.RED)
                );
            }
            this.todayKills = 0;
            setChanged();
            return;
        }

        this.energyStorage.extractEnergy(TurretConfig.PSYCHIC_BEACON_DAWN_COST.get(), false);

        if (level != null) {
            BlockPos chestPos = findChestPos(level, worldPosition);
            if (chestPos != null) {
                level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                BlockEntity chestBe = level.getBlockEntity(chestPos);
                if (chestBe instanceof ChestBlockEntity chest) {
                    fillVictoryChestDynamic(level, worldPosition, chest, this.threatLevel, this.todayKills);
                }
            } else {
                displayMessageToNearbyPlayers(
                    Component.translatable("message.flux_turret.beacon_no_chest_space")
                        .withStyle(net.minecraft.ChatFormatting.RED)
                );
            }

            level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0f, 1.0f);
            displayMessageToNearbyPlayers(
                Component.translatable("message.flux_turret.beacon_defense_success")
                    .withStyle(net.minecraft.ChatFormatting.AQUA)
            );
        }

        this.todayKills = 0;
        this.stability = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void displayMessageToNearbyPlayers(Component message) {
        if (level == null) return;
        AABB area = new AABB(worldPosition).inflate(50);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        for (Player player : players) {
            player.displayClientMessage(message, true);
        }
    }

    private static void spawnWave(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        AABB countArea = new AABB(pos).inflate(MONSTER_COUNT_RADIUS);
        List<Monster> existingMonsters = level.getEntitiesOfClass(Monster.class, countArea);
        int remaining = TurretConfig.PSYCHIC_BEACON_MAX_MONSTERS.get() - existingMonsters.size();
        if (remaining <= 0) return;

        int tl = Math.max(0, Math.min(4, be.threatLevel));
        RandomSource random = level.random;
        int waveBudget = Math.min(remaining, 2 + tl);

        int huskCount = Math.min(waveBudget, 1 + random.nextInt(2) + (tl >= 3 ? 1 : 0));
        waveBudget -= spawnHusks(level, pos, random, huskCount, tl);
        if (waveBudget <= 0) return;

        if (tl >= 1) {
            int spiderCount = Math.min(waveBudget, tl >= 3 ? 2 : 1);
            waveBudget -= spawnSpiders(level, pos, random, spiderCount, tl);
            if (waveBudget <= 0) return;
        }

        if (tl >= 3 && random.nextFloat() < 0.65f) {
            waveBudget -= spawnVexes(level, pos, random, Math.min(waveBudget, 1), tl);
            if (waveBudget <= 0) return;
        }

        if (tl >= 4 && random.nextFloat() < 0.35f) {
            waveBudget -= spawnChargedCreeper(level, pos, random);
            if (waveBudget <= 0) return;
        }

        if (be.stability < TurretConfig.PSYCHIC_BEACON_STABILITY.get() / 2 && random.nextFloat() < 0.5f) {
            spawnHusks(level, pos, random, Math.min(waveBudget, 1), tl);
        }
    }

    private static int spawnHusks(Level level, BlockPos pos, RandomSource random, int count, int threatLevel) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) continue;
            husk.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            applyEliteHealth(husk, getGroundEliteHealth(threatLevel, random));
            markBeaconSpawn(husk, pos);
            husk.goalSelector.addGoal(1, new MoveToBeaconGoal(husk, pos, 1.0D));
            level.addFreshEntity(husk);
            spawned++;
        }
        return spawned;
    }

    private static int spawnSpiders(Level level, BlockPos pos, RandomSource random, int count, int threatLevel) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Spider spider = EntityType.SPIDER.create(level);
            if (spider == null) continue;
            spider.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            applyEliteHealth(spider, getGroundEliteHealth(threatLevel, random));
            markBeaconSpawn(spider, pos);
            spider.goalSelector.addGoal(1, new MoveToBeaconGoal(spider, pos, 1.0D));
            level.addFreshEntity(spider);
            spawned++;
        }
        return spawned;
    }

    private static int spawnVexes(Level level, BlockPos pos, RandomSource random, int count, int threatLevel) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Vex vex = EntityType.VEX.create(level);
            if (vex == null) continue;
            vex.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            vex.setLimitedLife(2400);
            applyEliteHealth(vex, threatLevel >= 4 ? 60.0F : 40.0F);
            markBeaconSpawn(vex, pos);
            vex.goalSelector.addGoal(1, new MoveToBeaconGoal(vex, pos, 1.0D));
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
        creeper.goalSelector.addGoal(1, new MoveToBeaconGoal(creeper, pos, 1.0D));
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
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())) {
                return candidate;
            }
        }
        return null;
    }

    private static void broadcastBuffs(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        int radius = (be.threatLevel + 1) * 10;
        AABB area = new AABB(pos).inflate(radius);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        for (Player player : players) {
            switch (be.threatLevel) {
                case 1:
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, 0, true, true));
                    break;
                case 2:
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, 0, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, 0, true, true));
                    break;
                case 3:
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, 0, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, 0, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 260, 0, true, true));
                    break;
                case 4:
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 260, 1, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 260, 1, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 260, 0, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 260, 0, true, true));
                    break;
            }
        }
    }

    private static void failAndExplode(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        be.beaconState = STATE_FAILED;
        be.todayKills = 0;
        cleanupBeaconSpawnedMonsters(level, pos);

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

        level.setBlockAndUpdate(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState());
    }

    private static void emergencyShutdown(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        AABB clearArea = new AABB(pos).inflate(SPAWN_CLEANUP_RADIUS);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, clearArea);
        for (Monster monster : monsters) {
            if (isBeaconSpawn(monster, pos) || hasMoveToBeaconGoal(monster, pos)) {
                monster.discard();
            }
        }

        int cost = TurretConfig.PSYCHIC_BEACON_DAWN_COST.get();
        if (be.energyStorage.getEnergyStored() >= cost) {
            be.energyStorage.extractEnergy(cost, false);
        }

        be.beaconState = STATE_OFFLINE;
        be.stability = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        be.spawnTimer = 0;
        be.warningTimer = 0;
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
    }

    @Nullable
    private static BlockPos findChestPos(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            if (level.getBlockState(adjacent).isAir()
                    && level.getBlockState(adjacent.below()).isSolidRender(level, adjacent.below())) {
                return adjacent;
            }
        }
        BlockPos above = pos.above(2);
        if (level.getBlockState(above).isAir()) {
            return above;
        }
        return null;
    }

    private static void fillVictoryChestDynamic(Level level, BlockPos beaconPos, ChestBlockEntity chest, int threatLevel, int todayKills) {
        RandomSource random = level.random;

        int baseSlots = Math.min(1 + todayKills / 5, 5);
        int bonusSlots = threatLevel >= 3 ? 3 : threatLevel >= 2 ? 2 : 1;
        int totalFillSlots = Math.min(baseSlots + bonusSlots, chest.getContainerSize());

        for (int i = 0; i < totalFillSlots && i < 3; i++) {
            chest.setItem(i, new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(5)));
        }

        if (totalFillSlots > 3) {
            chest.setItem(3, new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4)));
        }

        if (threatLevel >= 1 && totalFillSlots > 4) {
            chest.setItem(4, new ItemStack(ModRegistry.ENERGY_CRYSTAL_ITEM.get(), 1 + random.nextInt(2)));
        }

        if (threatLevel >= 2 && totalFillSlots > 5) {
            chest.setItem(5, new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
        }

        if (threatLevel >= 3 && level instanceof ServerLevel serverLevel) {
            LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(
                    net.minecraft.world.level.storage.loot.BuiltInLootTables.END_CITY_TREASURE);
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(beaconPos))
                    .create(LootContextParamSets.CHEST);
            List<ItemStack> rewards = lootTable.getRandomItems(params);
            int slot = 6;
            for (ItemStack reward : rewards) {
                if (slot >= chest.getContainerSize()) break;
                chest.setItem(slot, reward);
                slot++;
            }
        }

        if (threatLevel >= 4 && random.nextFloat() < 0.15f) {
            chest.setItem(chest.getContainerSize() - 1, new ItemStack(Items.NETHER_STAR));
        }
    }

    @Nullable
    private static Player findNearestPlayer(Level level, BlockPos pos, int radius) {
        AABB area = new AABB(pos).inflate(radius);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        if (players.isEmpty()) return null;
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : players) {
            double dist = p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
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
    public Component getDisplayName() {
        return Component.translatable("container.flux_turret.psychic_beacon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PsychicBeaconMenu(containerId, playerInventory, this);
    }
}
