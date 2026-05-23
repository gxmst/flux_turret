package com.mymod.flux_turret.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
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
import java.util.List;

public abstract class TurretBlockEntityBase extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

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
        AABB scanArea = new AABB(pos).inflate(getTargetRange());
        monsterCache = level.getEntitiesOfClass(Monster.class, scanArea,
                m -> m.isAlive() && isValidTarget(m, level, pos));
    }

    protected Monster findClosestMonster(Level level, BlockPos pos) {
        return monsterCache.stream()
                .filter(m -> isValidTarget(m, level, pos))
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                .orElse(null);
    }

    protected void baseClientTick(Level level) {
        if (level.isClientSide) {
            if (visualCountdown > 0)
                visualCountdown--;
        }
    }

    protected void refreshMonsterCacheIfNeeded(Level level, BlockPos pos) {
        tickCounter++;
        if (tickCounter % getTargetCacheInterval() == 0 || monsterCache.isEmpty())
            refreshMonsterCache(level, pos);
    }

    public long getLastFireTime() {
        return lastFireTime;
    }

    public int getTargetId() {
        return targetId;
    }

    public boolean isVisuallyPowered() {
        return visualHasEnergy || energyStorage.getEnergyStored() >= getMinOperatingCost();
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
