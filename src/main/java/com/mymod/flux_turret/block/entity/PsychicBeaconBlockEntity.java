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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
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

    public int countNearbyTurrets(Class<? extends BlockEntity> type) {
        if (level == null) return 0;
        int count = 0;
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.hasChunk(cx + dx, cz + dz)) {
                    net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (type.isInstance(be) && be.getBlockPos().distManhattan(worldPosition) <= 32) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public long getTimeUntilDawn() {
        if (level == null) return 0;
        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 6000) {
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

        be.turretScanCooldown--;
        if (be.turretScanCooldown <= 0) {
            be.cachedTurretCounts[0] = be.countNearbyTurrets(PrismTowerBlockEntity.class);
            be.cachedTurretCounts[1] = be.countNearbyTurrets(TeslaCoilBlockEntity.class);
            be.cachedTurretCounts[2] = be.countNearbyTurrets(GatlingTurretBlockEntity.class);
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
            be.spawnTimer = 0;
        }
    }

    private static void tickActive(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        if (be.energyStorage.getEnergyStored() <= 0) {
            be.beaconState = STATE_OFFLINE;
            return;
        }

        if (level.hasNeighborSignal(pos)) {
            be.beaconState = STATE_WARNING;
            be.warningTimer = 60;
            Player nearest = findNearestPlayer(level, pos, 50);
            if (nearest != null) {
                nearest.displayClientMessage(Component.literal("\u00a7e[\u8b66\u544a] \u68c0\u6d4b\u5230\u5916\u90e8\u7ea2\u77f3\u5e72\u6270\uff01\u4fe1\u6807\u5b89\u5168\u9501\u5c06\u57283\u79d2\u5185\u65ad\u5f00\uff01"), true);
            }
            return;
        }

        be.energyStorage.extractEnergy(Math.min(TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get(), be.energyStorage.getEnergyStored()), false);

        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 13000 || dayTime >= 23000) {
            be.beaconState = STATE_IDLE;
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
                nearest.displayClientMessage(Component.literal("\u00a7a\u7ea2\u77f3\u4fe1\u53f7\u5df2\u65ad\u5f00\uff0c\u4fe1\u6807\u6062\u590d\u6218\u6597\u8fd0\u8f6c\uff01"), true);
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
                displayMessageToNearbyPlayers("\u00a7e[\u5fc3\u7075\u96f7\u8fbe] \u4eca\u65e5\u9632\u533a\u65e0\u8db3\u591f\u5b89\u5168\u5a01\u80c1\uff0c\u65e0\u987b\u964d\u4e0b\u8865\u7ed9\u7bb1\u3002");
            }
            this.todayKills = 0;
            setChanged();
            return;
        }

        if (this.energyStorage.getEnergyStored() < TurretConfig.PSYCHIC_BEACON_DAWN_COST.get()) {
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.5f);
                displayMessageToNearbyPlayers("\u00a7c[\u8b66\u544a] \u5fc3\u7075\u80fd\u91cf\u4e0d\u8db3 " + TurretConfig.PSYCHIC_BEACON_DAWN_COST.get() + " FE\uff01\u5b9d\u7bb1\u5408\u6210\u5931\u8d25\uff01");
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
                displayMessageToNearbyPlayers("\u00a7c[\u8b66\u544a] \u627e\u4e0d\u5230\u5408\u9002\u7684\u4f4d\u7f6e\u653e\u7f6e\u8865\u7ed9\u7bb1\uff01");
            }

            level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0f, 1.0f);
            displayMessageToNearbyPlayers("\u00a7b[\u5fc3\u7075\u96f7\u8fbe] \u4eca\u65e5\u9632\u533a\u6218\u5f79\u9632\u536b\u6210\u529f\uff01\u8865\u7ed9\u7bb1\u5df2\u5408\u6210\u964d\u4e0b\uff01");
        }

        this.todayKills = 0;
        this.stability = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void displayMessageToNearbyPlayers(String message) {
        if (level == null) return;
        AABB area = new AABB(worldPosition).inflate(50);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        for (Player player : players) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static void spawnWave(Level level, BlockPos pos, PsychicBeaconBlockEntity be) {
        AABB countArea = new AABB(pos).inflate(32);
        List<net.minecraft.world.entity.monster.Monster> existingMonsters = level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class, countArea);
        if (existingMonsters.size() >= TurretConfig.PSYCHIC_BEACON_MAX_MONSTERS.get()) return;

        int tl = be.threatLevel;
        RandomSource random = level.random;

        spawnHusks(level, pos, random, tl >= 0 ? 2 + random.nextInt(2) : 0);

        if (tl >= 2) {
            spawnSpiders(level, pos, random, 1 + random.nextInt(2));
        }

        if (tl >= 3) {
            spawnVexes(level, pos, random, 1 + random.nextInt(2));
        }

        if (tl >= 4) {
            spawnChargedCreeper(level, pos, random);
        }
    }

    private static void spawnHusks(Level level, BlockPos pos, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) continue;
            husk.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            husk.setPersistenceRequired();
            husk.goalSelector.addGoal(1, new MoveToBeaconGoal(husk, pos, 1.0D));
            level.addFreshEntity(husk);
        }
    }

    private static void spawnSpiders(Level level, BlockPos pos, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Spider spider = EntityType.SPIDER.create(level);
            if (spider == null) continue;
            spider.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            spider.setPersistenceRequired();
            spider.goalSelector.addGoal(1, new MoveToBeaconGoal(spider, pos, 1.0D));
            level.addFreshEntity(spider);
        }
    }

    private static void spawnVexes(Level level, BlockPos pos, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPos(level, pos, random);
            if (spawnPos == null) continue;
            Vex vex = EntityType.VEX.create(level);
            if (vex == null) continue;
            vex.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            vex.setLimitedLife(2400);
            vex.setPersistenceRequired();
            vex.goalSelector.addGoal(1, new MoveToBeaconGoal(vex, pos, 1.0D));
            level.addFreshEntity(vex);
        }
    }

    private static void spawnChargedCreeper(Level level, BlockPos pos, RandomSource random) {
        BlockPos spawnPos = findSpawnPos(level, pos, random);
        if (spawnPos == null) return;
        Creeper creeper = EntityType.CREEPER.create(level);
        if (creeper == null) return;
        creeper.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, random.nextFloat() * 360F, 0);
        creeper.getEntityData().set(Creeper.DATA_IS_POWERED, true);
        creeper.setPersistenceRequired();
        creeper.goalSelector.addGoal(1, new MoveToBeaconGoal(creeper, pos, 1.0D));
        level.addFreshEntity(creeper);
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

        level.explode(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 5.0f, Level.ExplosionInteraction.NONE);

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
        AABB clearArea = new AABB(pos).inflate(32);
        List<net.minecraft.world.entity.monster.Monster> monsters = level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class, clearArea);
        for (net.minecraft.world.entity.monster.Monster monster : monsters) {
            if (monster.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof MoveToBeaconGoal)) {
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
