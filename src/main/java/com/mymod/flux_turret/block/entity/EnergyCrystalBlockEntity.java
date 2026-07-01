package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.EnergyCrystalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

public class EnergyCrystalBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final int TICK_INTERVAL = 5;

    private final ConfigurableEnergyStorage energyStorage;
    private LazyOptional<IEnergyStorage> energyCap;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int activeTicks = 0;
    private boolean charging = false;
    private int tickCounter = 0;

    public EnergyCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENERGY_CRYSTAL_BE.get(), pos, state);
        this.energyStorage = new ConfigurableEnergyStorage();
        this.energyCap = LazyOptional.of(() -> this.energyStorage);
    }

    public EnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getEnergyMultiplier() {
        if (getBlockState().getBlock() instanceof EnergyCrystalBlock crystalBlock) {
            return crystalBlock.getEnergyMultiplier();
        }
        return 1;
    }

    public boolean isEmpowered() {
        return getEnergyMultiplier() > 1;
    }

    public void setEnergyStored(int energy) {
        energyStorage.setEnergy(energy);
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnergyCrystalBlockEntity be) {
        if (level.isClientSide) {
            if (be.activeTicks > 0) {
                be.activeTicks--;
            }
            return;
        }

        be.energyStorage.applyConfig();
        be.tickCounter++;
        if (be.tickCounter % TICK_INTERVAL != 0) return;

        int capacity = be.energyStorage.getMaxEnergyStored();
        boolean wasFull = state.hasProperty(EnergyCrystalBlock.FULL) && state.getValue(EnergyCrystalBlock.FULL);
        int chargeRate = TurretConfig.ENERGY_CRYSTAL_CHARGE_RATE.get() * be.getEnergyMultiplier() * TICK_INTERVAL;
        int maxOutput = TurretConfig.ENERGY_CRYSTAL_MAX_OUTPUT.get() * be.getEnergyMultiplier() * TICK_INTERVAL;

        // Charge from lit furnace below
        boolean wasCharging = be.charging;
        be.charging = false;
        if (be.energyStorage.getEnergyStored() < capacity) {
            BlockState belowState = level.getBlockState(pos.below());
            if (belowState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && belowState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                int received = be.energyStorage.receiveEnergy(chargeRate, false);
                if (received > 0) {
                    be.charging = true;
                    be.setChanged();
                }
            }
        }

        // Auto-push energy to all adjacent blocks
        boolean[] transferred = {false};
        for (Direction dir : Direction.values()) {
            if (be.energyStorage.getEnergyStored() <= 0) break;
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBe = level.getBlockEntity(neighborPos);
            if (neighborBe == null) continue;
            LazyOptional<IEnergyStorage> neighborCap = neighborBe.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite());
            neighborCap.ifPresent(neighborStorage -> {
                if (neighborStorage.canReceive()) {
                    int toExtract = be.energyStorage.extractEnergy(maxOutput, true);
                    if (toExtract > 0) {
                        int received = neighborStorage.receiveEnergy(toExtract, false);
                        if (received > 0) {
                            be.energyStorage.extractEnergy(received, false);
                            be.setChanged();
                            transferred[0] = true;
                        }
                    }
                }
            });
        }

        int prevActiveTicks = be.activeTicks;
        if (transferred[0] || be.charging) {
            be.activeTicks = 20;
        } else if (be.activeTicks > 0) {
            be.activeTicks--;
        }

        boolean isCharged = be.energyStorage.getEnergyStored() > 0;
        if (state.hasProperty(EnergyCrystalBlock.FULL) && wasFull != isCharged) {
            level.setBlock(pos, state.setValue(EnergyCrystalBlock.FULL, isCharged), 3);
        }

        if ((be.activeTicks > 0) != (prevActiveTicks > 0) || be.charging != wasCharging) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Energy")) {
            energyStorage.deserializeNBT(tag.get("Energy"));
        }
        activeTicks = tag.getInt("ActiveTicks");
        charging = tag.getBoolean("Charging");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Energy", energyStorage.serializeNBT());
        tag.putInt("ActiveTicks", activeTicks);
        tag.putBoolean("Charging", charging);
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
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 0, state -> {
            if (this.activeTicks > 0) {
                return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.energy_crystal.active"));
            }
            return state.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("animation.energy_crystal.idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private class ConfigurableEnergyStorage extends EnergyStorage {
        ConfigurableEnergyStorage() {
            super(TurretConfig.ENERGY_CRYSTAL_CAPACITY.get(),
                    TurretConfig.ENERGY_CRYSTAL_CAPACITY.get(),
                    TurretConfig.ENERGY_CRYSTAL_MAX_OUTPUT.get() * TICK_INTERVAL, 0);
            applyConfig();
        }

        void applyConfig() {
            this.capacity = getConfiguredCapacity();
            this.maxReceive = this.capacity;
            this.maxExtract = getConfiguredMaxExtract();
            this.energy = Math.max(0, Math.min(this.energy, this.capacity));
        }

        void setEnergy(int energy) {
            applyConfig();
            this.energy = Math.max(0, Math.min(energy, this.capacity));
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyConfig();
            return super.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            applyConfig();
            return super.extractEnergy(maxExtract, simulate);
        }

        // Read-only getters intentionally skip applyConfig(): the backing fields
        // (energy/capacity/maxExtract) are refreshed once per tick by the block
        // entity's tick() and by every write path below. Neighboring turrets poll
        // these getters many times per tick when pulling power, so re-reading the
        // Forge config on each call was pure overhead. A config reload is reflected
        // on the next tick at the latest.
        @Override
        public int getEnergyStored() {
            return super.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return super.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return super.canExtract();
        }

        @Override
        public boolean canReceive() {
            return super.canReceive();
        }

        @Override
        public Tag serializeNBT() {
            applyConfig();
            return super.serializeNBT();
        }

        @Override
        public void deserializeNBT(Tag nbt) {
            applyConfig();
            if (nbt instanceof CompoundTag compoundTag) {
                setEnergy(compoundTag.getInt("energy"));
                return;
            }
            super.deserializeNBT(nbt);
            applyConfig();
        }

        private int getConfiguredCapacity() {
            return TurretConfig.ENERGY_CRYSTAL_CAPACITY.get() * EnergyCrystalBlockEntity.this.getEnergyMultiplier();
        }

        private int getConfiguredMaxExtract() {
            return TurretConfig.ENERGY_CRYSTAL_MAX_OUTPUT.get() * EnergyCrystalBlockEntity.this.getEnergyMultiplier() * TICK_INTERVAL;
        }
    }

    @Override
    public @org.jetbrains.annotations.NotNull <T> LazyOptional<T> getCapability(@org.jetbrains.annotations.NotNull net.minecraftforge.common.capabilities.Capability<T> cap, @org.jetbrains.annotations.Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
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
}
