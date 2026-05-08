package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import java.util.List;

public class GatlingTurretBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final int CAPACITY = 60000;
    private static final int MAX_RECEIVE = 800;
    private static final int FIRE_COST = 30;
    private static final double TARGET_RANGE = 11.0;
    private static final float DAMAGE = 0.5f;
    private static final int MIN_FIRE_INTERVAL = 2;
    private static final int MAX_FIRE_INTERVAL = 12;
    private static final int MAX_SPIN = 100;
    private static final int TARGET_CACHE_INTERVAL = 6;
    private static final int SOUND_INTERVAL = 4;

    public int visualCountdown = 0;
    public int visualTargetId = -1;
    public Vec3 visualCachedTargetPos = null;
    public boolean visualHasEnergy = false;

    private int targetId = -1;
    private int attackCooldown = 0;
    private int spinUp = 0;
    private boolean isFiring = false;
    private long lastFireTime = 0;
    private long lastSoundTime = 0;
    private int tickCounter = 0;
    private List<Monster> monsterCache = List.of();

    private static class GatlingEnergyStorage extends EnergyStorage {
        public GatlingEnergyStorage(int capacity, int maxReceive) {
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

    private final GatlingEnergyStorage energyStorage = new GatlingEnergyStorage(CAPACITY, MAX_RECEIVE) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate)
                setChanged();
            return received;
        }
    };

    private LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);

    public GatlingTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GATLING_TURRET_BE.get(), pos, state);
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
        tag.putInt("SpinUp", spinUp);
        tag.putBoolean("IsFiring", isFiring);
        tag.putLong("LastFireTime", lastFireTime);
        tag.putBoolean("HasPower", visualHasEnergy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt("Energy"));
        targetId = tag.getInt("TargetId");
        spinUp = tag.getInt("SpinUp");
        isFiring = tag.getBoolean("IsFiring");
        lastFireTime = tag.getLong("LastFireTime");
        visualHasEnergy = tag.getBoolean("HasPower");
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

        load(tag);
        visualHasEnergy = energyStorage.getEnergyStored() >= FIRE_COST;
        visualTargetId = targetId;
        visualCachedTargetPos = null;

        if (isFiring && targetId != -1) {
            visualCountdown = 3;
            if (level != null) {
                Entity target = level.getEntity(targetId);
                if (target != null)
                    visualCachedTargetPos = target.getEyePosition(0.0f);
            }
        } else {
            visualCountdown = 0;
        }
    }

    private boolean isValidTarget(Monster monster, Level level, BlockPos selfPos) {
        if (!monster.isAlive()) return false;
        Vec3 eyePos = new Vec3(selfPos.getX() + 0.5, selfPos.getY() + 1.4, selfPos.getZ() + 0.5);
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

    private void refreshMonsterCache(Level level, BlockPos pos) {
        AABB scanArea = new AABB(pos).inflate(TARGET_RANGE);
        monsterCache = level.getEntitiesOfClass(Monster.class, scanArea,
                m -> m.isAlive() && isValidTarget(m, level, pos));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GatlingTurretBlockEntity be) {
        if (level.isClientSide) {
            if (be.visualCountdown > 0)
                be.visualCountdown--;
            return;
        }

        be.tickCounter++;
        if (be.tickCounter % TARGET_CACHE_INTERVAL == 0 || be.monsterCache.isEmpty())
            be.refreshMonsterCache(level, pos);

        int prevTargetId = be.targetId;
        int prevSpinUp = be.spinUp;
        boolean prevFiring = be.isFiring;
        long prevFireTime = be.lastFireTime;
        boolean prevHasEnergy = be.visualHasEnergy;

        boolean hasEnoughEnergy = be.energyStorage.getEnergyStored() >= FIRE_COST;
        be.visualHasEnergy = hasEnoughEnergy;

        if (be.attackCooldown > 0)
            be.attackCooldown--;

        Monster target = hasEnoughEnergy ? be.monsterCache.stream()
                .filter(m -> be.isValidTarget(m, level, pos))
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                .orElse(null) : null;

        if (target == null) {
            be.targetId = -1;
            be.isFiring = false;
            be.spinUp = Math.max(0, be.spinUp - 5);
        } else {
            be.targetId = target.getId();
            be.spinUp = Math.min(MAX_SPIN, be.spinUp + 6);
            int interval = getFireInterval(be.spinUp);

            if (be.attackCooldown <= 0 && be.energyStorage.consumeEnergy(FIRE_COST)) {
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().magic(), DAMAGE);
                if (level.getGameTime() - be.lastSoundTime >= SOUND_INTERVAL) {
                    level.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 0.45f, 1.75f);
                    level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.25f, 1.35f);
                    be.lastSoundTime = level.getGameTime();
                }
                be.isFiring = true;
                be.lastFireTime = level.getGameTime();
                be.attackCooldown = interval;
            } else {
                be.isFiring = false;
            }
        }

        if (be.targetId != prevTargetId || be.spinUp != prevSpinUp || be.isFiring != prevFiring
                || be.lastFireTime != prevFireTime || be.visualHasEnergy != prevHasEnergy) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static int getFireInterval(int spinUp) {
        float t = Math.max(0, Math.min(MAX_SPIN, spinUp)) / (float) MAX_SPIN;
        return Math.max(MIN_FIRE_INTERVAL, Math.round(MAX_FIRE_INTERVAL + (MIN_FIRE_INTERVAL - MAX_FIRE_INTERVAL) * t));
    }

    public long getLastFireTime() {
        return lastFireTime;
    }

    public int getTargetId() {
        return targetId;
    }

    public int getSpinUp() {
        return spinUp;
    }

    public boolean isVisuallyPowered() {
        return visualHasEnergy || energyStorage.getEnergyStored() >= FIRE_COST;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(12, 8, 12);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
