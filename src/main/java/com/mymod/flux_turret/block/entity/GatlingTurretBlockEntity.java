package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.util.TurretVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class GatlingTurretBlockEntity extends TurretBlockEntityBase {
    private static final int MAX_RECEIVE = 800;
    private static final int MIN_FIRE_INTERVAL = 2;
    private static final int MAX_FIRE_INTERVAL = 14;
    private static final int MAX_SPIN = 200;
    private static final int MIN_SPIN_TO_FIRE = 30;
    private static final int TARGET_CACHE_INTERVAL = 10;
    private static final int SOUND_INTERVAL = 4;
    static final int FIRE_PACKET_INTERVAL = 4;
    static final int DAMAGE_WINDOW_TICKS = 10;
    private static final int PENDING_TARGET_EXPIRY_TICKS = 200;
    private static final int MAX_PENDING_TARGETS = 16;
    private static final float MAX_PENDING_DAMAGE = 1_000_000.0f;
    private static final String PENDING_DAMAGE_TAG = "PendingDamage";
    private static final ResourceKey<DamageType> GATLING_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "gatling"));

    private int spinUp = 0;
    private long lastSoundTime = 0;
    private DamageSource cachedDamageSource;
    private final Map<UUID, DamageBatch> pendingDamage = new HashMap<>();
    private final FirePacketGate firePacketGate = new FirePacketGate();

    static final class DamageBatch {
        final UUID targetUuid;
        float damage;
        int shotCount;
        long settleAt;
        long discardAt;
        boolean armorAdjusted = true;

        DamageBatch(UUID targetUuid) {
            this.targetUuid = targetUuid;
        }

        void addShot(float shotDamage, long gameTime) {
            if (shotCount == 0) {
                settleAt = gameTime + DAMAGE_WINDOW_TICKS;
            }
            damage += shotDamage;
            shotCount++;
            discardAt = gameTime + PENDING_TARGET_EXPIRY_TICKS;
        }

        boolean isDue(long gameTime) {
            return shotCount > 0 && gameTime >= settleAt;
        }

        boolean isExpired(long gameTime) {
            return shotCount > 0 && gameTime >= discardAt;
        }

        static boolean isDamageWindowOpen(int invulnerableTime) {
            return invulnerableTime <= DAMAGE_WINDOW_TICKS;
        }

        static float armorAdjustedShotDamage(float rawDamage, float armor, float toughness) {
            return CombatRules.getDamageAfterAbsorb(rawDamage, armor, toughness);
        }

        boolean normalizeLegacyDamage(float armor, float toughness) {
            if (armorAdjusted || shotCount <= 0) return false;
            float averageRawDamage = damage / shotCount;
            damage = armorAdjustedShotDamage(averageRawDamage, armor, toughness) * shotCount;
            armorAdjusted = true;
            return true;
        }
    }

    static final class FirePacketGate {
        long lastPacketTime = Long.MIN_VALUE;
        long lastShotTime = Long.MIN_VALUE;
        int lastPacketTargetId = -1;

        boolean onShot(long gameTime, int targetId) {
            boolean firstShot = lastShotTime == Long.MIN_VALUE
                    || gameTime - lastShotTime > FIRE_PACKET_INTERVAL;
            boolean targetChanged = targetId != lastPacketTargetId;
            boolean intervalElapsed = lastPacketTime == Long.MIN_VALUE
                    || gameTime - lastPacketTime >= FIRE_PACKET_INTERVAL;
            lastShotTime = gameTime;
            if (!firstShot && !targetChanged && !intervalElapsed) return false;

            lastPacketTime = gameTime;
            lastPacketTargetId = targetId;
            return true;
        }
    }

    public GatlingTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GATLING_TURRET_BE.get(), pos, state, TurretConfig.GATLING_CAPACITY.get(), MAX_RECEIVE);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 0, state -> {
            if (this.isVisuallyPowered()) {
                if (this.visualCountdown > 0) {
                    return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.gatling_turret.active"));
                }
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.gatling_turret.idle"));
            }
            return software.bernie.geckolib.core.object.PlayState.STOP;
        }));
    }

    @Override
    protected double getTargetRange() {
        return TurretConfig.GATLING_RANGE.get();
    }

    @Override
    protected double getEyeHeight() {
        return 1.25;
    }

    @Override
    protected int getTargetCacheInterval() {
        return TARGET_CACHE_INTERVAL;
    }

    @Override
    protected int getFiringVisualCountdown() {
        return FIRE_PACKET_INTERVAL + 2;
    }

    @Override
    protected int getMinOperatingCost() {
        return TurretConfig.GATLING_FIRE_COST.get();
    }

    @Override
    protected TargetingMode getAutomaticTargetingMode() {
        return TargetingMode.FASTEST;
    }

    @Override
    protected boolean isWarmingUpForDiagnostics() {
        return targetId != -1 && spinUp < MIN_SPIN_TO_FIRE;
    }

    @Override
    public boolean canInstallUpgrade(TurretUpgradeType type) {
        return type == TurretUpgradeType.ARMOR_PIERCING_ROUNDS
                || type == TurretUpgradeType.FIRE_ROUNDS
                || type == TurretUpgradeType.SLOW_ROUNDS;
    }

    @Override
    protected void saveAdditionalTurret(CompoundTag tag) {
        tag.putInt("SpinUp", spinUp);
        if (!pendingDamage.isEmpty()) {
            ListTag batches = new ListTag();
            for (DamageBatch batch : pendingDamage.values()) {
                if (batch.shotCount <= 0 || batch.damage <= 0.0f || !Float.isFinite(batch.damage)) continue;
                CompoundTag batchTag = new CompoundTag();
                batchTag.putUUID("Target", batch.targetUuid);
                batchTag.putFloat("Damage", batch.damage);
                batchTag.putInt("Shots", batch.shotCount);
                batchTag.putLong("SettleAt", batch.settleAt);
                batchTag.putLong("DiscardAt", batch.discardAt);
                batchTag.putBoolean("ArmorAdjusted", batch.armorAdjusted);
                batches.add(batchTag);
            }
            if (!batches.isEmpty()) {
                tag.put(PENDING_DAMAGE_TAG, batches);
            }
        }
    }

    @Override
    protected void loadAdditionalTurret(CompoundTag tag) {
        spinUp = tag.getInt("SpinUp");
        pendingDamage.clear();
        ListTag batches = tag.getList(PENDING_DAMAGE_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < batches.size() && pendingDamage.size() < MAX_PENDING_TARGETS; i++) {
            CompoundTag batchTag = batches.getCompound(i);
            if (!batchTag.hasUUID("Target")) continue;
            float damage = batchTag.getFloat("Damage");
            int shots = batchTag.getInt("Shots");
            if (!Float.isFinite(damage) || damage <= 0.0f || damage > MAX_PENDING_DAMAGE || shots <= 0) continue;

            DamageBatch batch = new DamageBatch(batchTag.getUUID("Target"));
            batch.damage = damage;
            batch.shotCount = shots;
            batch.settleAt = batchTag.getLong("SettleAt");
            batch.discardAt = Math.max(batch.settleAt, batchTag.getLong("DiscardAt"));
            batch.armorAdjusted = batchTag.getBoolean("ArmorAdjusted");
            pendingDamage.putIfAbsent(batch.targetUuid, batch);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove(PENDING_DAMAGE_TAG);
        return tag;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GatlingTurretBlockEntity be) {
        if (level.isClientSide) {
            be.baseClientTick(level);
            if (be.visualTargetId != -1 || be.visualCountdown > 0) {
                be.spinUp = Math.min(MAX_SPIN, be.spinUp + 3);
            } else {
                be.spinUp = Math.max(0, be.spinUp - 4);
            }
            return;
        }

        be.settlePendingDamage(level);
        be.flushThrottledUpdate();

        int prevTargetId = be.targetId;
        int prevSpinUp = be.spinUp;
        boolean prevHasEnergy = be.visualHasEnergy;

        if (be.isRedstoneBlocked(level, pos)) {
            be.targetId = -1;
            be.isFiring = false;
            be.spinUp = Math.max(0, be.spinUp - 4);
            be.clearAimTarget();
            be.visualHasEnergy = be.getEnergyStorage().getEnergyStored() >= TurretConfig.GATLING_FIRE_COST.get();
            if (be.targetId != prevTargetId || be.visualHasEnergy != prevHasEnergy) {
                be.requestThrottledUpdate();
            }
            be.flushThrottledUpdate();
            return;
        }

        int fireCost = TurretConfig.GATLING_FIRE_COST.get();
        boolean hasEnoughEnergy = be.getEnergyStorage().getEnergyStored() >= fireCost;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0) {
            be.attackCooldown--;
            if (be.attackCooldown == 0 || isPositionScheduledTick(level.getGameTime(), pos, 20)) be.setChanged();
        }

        if (hasEnoughEnergy) {
            be.refreshMonsterCacheIfNeeded(level, pos);
        } else {
            be.monsterCache = java.util.List.of();
        }

        Mob target = hasEnoughEnergy ? be.findClosestMonster(level, pos) : null;

        if (target == null) {
            be.targetId = -1;
            be.isFiring = false;
            be.spinUp = Math.max(0, be.spinUp - 4);
            be.clearAimTarget();
        } else {
            be.targetId = target.getId();
            Vec3 aim = target.getEyePosition(0.0f);
            be.setAimTarget(aim.x, aim.y, aim.z);
            be.spinUp = Math.min(MAX_SPIN, be.spinUp + 3);

            if (be.spinUp < MIN_SPIN_TO_FIRE) {
                be.isFiring = false;
            } else if (be.attackCooldown <= 0) {
                int interval = getFireInterval(be.spinUp);
                float rawDamage = TurretConfig.GATLING_DAMAGE.get().floatValue();
                if (be.hasUpgrade(TurretUpgradeType.ARMOR_PIERCING_ROUNDS)) {
                    rawDamage += Math.min(4.0f, target.getArmorValue() * 0.22f + rawDamage * 0.25f);
                }
                float damage = DamageBatch.armorAdjustedShotDamage(
                        rawDamage,
                        target.getArmorValue(),
                        (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                if (be.canQueueShot(target.getUUID(), damage)
                        && be.getEnergyStorage().consumeEnergy(fireCost)) {
                    be.queueShot(target.getUUID(), damage, level.getGameTime());
                    if (be.hasUpgrade(TurretUpgradeType.FIRE_ROUNDS)) {
                        target.setSecondsOnFire(4);
                    }
                    if (be.hasUpgrade(TurretUpgradeType.SLOW_ROUNDS)) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, true));
                    }

                    Vec3 targetPos = target.getEyePosition(0.0f);

                    // Sound with pitch variation based on spin speed
                    if (level.getGameTime() - be.lastSoundTime >= SOUND_INTERVAL) {
                        float spinProgress = be.spinUp / (float) MAX_SPIN;
                        float basePitch = 0.9f + spinProgress * 0.3f; // Higher pitch when spinning faster
                        TurretVisualEffects.playTurretSound(level, pos, ModRegistry.GATLING_SHOOT.get(),
                            0.5f, basePitch, 0.1f);
                        be.lastSoundTime = level.getGameTime();
                    }

                    be.isFiring = true;
                    be.lastFireTime = level.getGameTime();
                    be.attackCooldown = interval;
                    be.setChanged();
                    if (be.firePacketGate.onShot(level.getGameTime(), target.getId())) {
                        be.sendFirePacket(targetPos, java.util.List.of(), 0, 0.0f);
                    }
                } else {
                    be.isFiring = false;
                }
            } else {
                be.isFiring = false;
            }
        }

        if (be.spinUp != prevSpinUp
                && (be.spinUp == 0 || be.spinUp == MAX_SPIN
                || isPositionScheduledTick(level.getGameTime(), pos, 20))) {
            be.setChanged();
        }
        if (be.targetId != prevTargetId || be.visualHasEnergy != prevHasEnergy
                || be.aimDriftedSinceSync()) {
            be.requestThrottledUpdate();
        }
        be.flushThrottledUpdate();
    }

    private boolean canQueueShot(UUID targetUuid, float damage) {
        if (!Float.isFinite(damage) || damage <= 0.0f || damage > MAX_PENDING_DAMAGE) return false;
        DamageBatch batch = pendingDamage.get(targetUuid);
        if (batch == null) return pendingDamage.size() < MAX_PENDING_TARGETS;
        return batch.damage <= MAX_PENDING_DAMAGE - damage && batch.shotCount < Integer.MAX_VALUE;
    }

    private void queueShot(UUID targetUuid, float damage, long gameTime) {
        pendingDamage.computeIfAbsent(targetUuid, DamageBatch::new).addShot(damage, gameTime);
        setChanged();
    }

    private void settlePendingDamage(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || pendingDamage.isEmpty()) return;

        long gameTime = level.getGameTime();
        boolean changed = false;
        Iterator<DamageBatch> iterator = pendingDamage.values().iterator();
        while (iterator.hasNext()) {
            DamageBatch batch = iterator.next();
            Entity entity = serverLevel.getEntity(batch.targetUuid);
            Mob target = entity instanceof Mob mob ? mob : null;
            if (entity != null && (target == null || !isEnemyTarget(target))) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (!batch.isDue(gameTime)) {
                if (target != null && batch.normalizeLegacyDamage(
                        target.getArmorValue(),
                        (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))) {
                    changed = true;
                }
                continue;
            }
            if (batch.isExpired(gameTime)) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (target == null) continue;

            if (batch.normalizeLegacyDamage(
                    target.getArmorValue(),
                    (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))) {
                changed = true;
            }
            if (!DamageBatch.isDamageWindowOpen(target.invulnerableTime)) continue;

            target.hurt(getGatlingDamageSource(level), batch.damage);
            iterator.remove();
            changed = true;
        }
        if (changed) setChanged();
    }

    private static int getFireInterval(int spinUp) {
        // Linear ramp from MAX_FIRE_INTERVAL (slow) down to MIN_FIRE_INTERVAL (fast)
        // so the rate-up reads as a smooth, continuous acceleration rather than a
        // last-moment jump (the old t*t curve kept it slow for most of the spin).
        float t = Math.max(0, Math.min(MAX_SPIN, spinUp)) / (float) MAX_SPIN;
        return Math.max(MIN_FIRE_INTERVAL, Math.round(MAX_FIRE_INTERVAL + (MIN_FIRE_INTERVAL - MAX_FIRE_INTERVAL) * t));
    }

    public int getCurrentFireInterval() {
        return getFireInterval(spinUp);
    }

    private DamageSource getGatlingDamageSource(Level level) {
        if (cachedDamageSource == null) {
            cachedDamageSource = new DamageSource(level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(GATLING_DAMAGE),
                    Vec3.atCenterOf(worldPosition).add(0.0, 1.2, 0.0));
        }
        return cachedDamageSource;
    }

    public int getSpinUp() {
        return spinUp;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(12, 8, 12);
    }
}
