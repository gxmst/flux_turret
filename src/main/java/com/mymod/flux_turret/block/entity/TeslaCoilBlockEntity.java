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

public class TeslaCoilBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final int CAPACITY = 120000;
    private static final int MAX_RECEIVE = 1200;
    private static final int FIRE_COST = 1400;
    private static final double TARGET_RANGE = 18.5;
    private static final int WARMUP_TICKS = 8;
    private static final int ATTACK_COOLDOWN = 24;
    private static final float DAMAGE = 12.0f;
    private static final int TARGET_CACHE_INTERVAL = 8;

    public int visualCountdown = 0;
    public int visualTargetId = -1;
    public Vec3 visualCachedTargetPos = null;
    public boolean visualHasEnergy = false;

    private int targetId = -1;
    private int attackCooldown = 0;
    private int warmupTicks = 0;
    private boolean isFiring = false;
    private long lastFireTime = 0;
    private int tickCounter = 0;
    private List<Monster> monsterCache = List.of();

    private static class TeslaEnergyStorage extends EnergyStorage {
        public TeslaEnergyStorage(int capacity, int maxReceive) {
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

    private final TeslaEnergyStorage energyStorage = new TeslaEnergyStorage(CAPACITY, MAX_RECEIVE) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate)
                setChanged();
            return received;
        }
    };

    private LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> energyStorage);

    public TeslaCoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TESLA_COIL_BE.get(), pos, state);
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt("Energy"));
        targetId = tag.getInt("TargetId");
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
            visualCountdown = 7;
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
        Vec3 eyePos = new Vec3(selfPos.getX() + 0.5, selfPos.getY() + 2.9, selfPos.getZ() + 0.5);
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

    public static void tick(Level level, BlockPos pos, BlockState state, TeslaCoilBlockEntity be) {
        if (level.isClientSide) {
            if (be.visualCountdown > 0)
                be.visualCountdown--;
            return;
        }

        be.tickCounter++;
        if (be.tickCounter % TARGET_CACHE_INTERVAL == 0 || be.monsterCache.isEmpty())
            be.refreshMonsterCache(level, pos);

        int prevTargetId = be.targetId;
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
            be.warmupTicks = 0;
        } else {
            be.targetId = target.getId();
            if (be.attackCooldown <= 0) {
                be.warmupTicks++;
                if (be.warmupTicks >= WARMUP_TICKS) {
                    if (be.energyStorage.consumeEnergy(FIRE_COST)) {
                        target.hurt(level.damageSources().magic(), DAMAGE);
                        level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER, SoundSource.BLOCKS, 0.75f, 1.65f);
                        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.45f, 1.85f);
                        be.isFiring = true;
                        be.lastFireTime = level.getGameTime();
                        be.attackCooldown = ATTACK_COOLDOWN;
                        be.warmupTicks = 0;
                    }
                } else {
                    be.isFiring = false;
                    if (be.warmupTicks == 1)
                        level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.35f, 1.9f);
                }
            } else {
                be.isFiring = false;
                be.warmupTicks = 0;
            }
        }

        if (be.targetId != prevTargetId || be.isFiring != prevFiring
                || be.lastFireTime != prevFireTime || be.visualHasEnergy != prevHasEnergy) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public long getLastFireTime() {
        return lastFireTime;
    }

    public int getTargetId() {
        return targetId;
    }

    public boolean isVisuallyPowered() {
        return visualHasEnergy || energyStorage.getEnergyStored() >= FIRE_COST;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(24, 15, 24);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
