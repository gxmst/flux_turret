package com.mymod.flux_turret.block.entity;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.EnergyCrystalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

public class EnergyCrystalBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final int TICK_INTERVAL = 5;
    private static final int ACTIVE_DURATION_TICKS = 20;
    private static final Direction[] OUTPUT_DIRECTIONS = Direction.values();

    private final ConfigurableEnergyStorage energyStorage;
    private LazyOptional<IEnergyStorage> energyCap;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int activeTicks = 0;
    private boolean charging = false;
    private int tickCounter = 0;
    private int outputDirectionCursor = 0;

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
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnergyCrystalBlockEntity be) {
        if (level.isClientSide) return;

        be.energyStorage.applyConfig();
        be.energyStorage.refreshOutputBudget(level.getGameTime());
        be.syncHasEnergyState();

        boolean wasActive = be.activeTicks > 0;
        if (be.activeTicks > 0) {
            be.activeTicks--;
        }

        be.tickCounter++;
        if (be.tickCounter % TICK_INTERVAL != 0) {
            if (wasActive && be.activeTicks == 0) {
                be.setChanged();
                BlockState currentState = be.getBlockState();
                level.sendBlockUpdated(pos, currentState, currentState, 3);
            }
            return;
        }

        int capacity = be.energyStorage.getMaxEnergyStored();
        int chargeRate = TurretConfig.ENERGY_CRYSTAL_CHARGE_RATE.get() * be.getEnergyMultiplier() * TICK_INTERVAL;

        // Charge from lit furnace below
        boolean wasCharging = be.charging;
        be.charging = false;
        if (be.energyStorage.getEnergyStored() < capacity) {
            BlockState belowState = level.getBlockState(pos.below());
            if (belowState.getBlock() instanceof AbstractFurnaceBlock
                    && belowState.hasProperty(BlockStateProperties.LIT)
                    && belowState.getValue(BlockStateProperties.LIT)) {
                int received = be.energyStorage.receiveEnergy(chargeRate, false);
                if (received > 0) {
                    be.charging = true;
                }
            }
        }

        // Auto-push energy using one shared five-tick budget. Rotating the first
        // direction prevents a permanently energy-starved side when demand exceeds
        // the configured total output.
        boolean transferred = false;
        int remainingOutput = be.energyStorage.getAvailableOutputBudget();
        int nextOutputDirectionCursor = (be.outputDirectionCursor + 1) % OUTPUT_DIRECTIONS.length;
        for (int i = 0; i < OUTPUT_DIRECTIONS.length && remainingOutput > 0; i++) {
            int directionIndex = (be.outputDirectionCursor + i) % OUTPUT_DIRECTIONS.length;
            Direction dir = OUTPUT_DIRECTIONS[directionIndex];
            if (be.energyStorage.getEnergyStored() <= 0) break;
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBe = level.getBlockEntity(neighborPos);
            if (neighborBe == null) continue;
            IEnergyStorage neighborStorage = neighborBe
                    .getCapability(ForgeCapabilities.ENERGY, dir.getOpposite())
                    .resolve()
                    .orElse(null);
            if (neighborStorage == null || !neighborStorage.canReceive()) continue;

            int offered = be.energyStorage.extractEnergy(remainingOutput, true);
            if (offered <= 0) continue;

            int received = neighborStorage.receiveEnergy(offered, false);
            int accepted = Math.min(offered, Math.max(0, received));
            if (accepted <= 0) continue;

            int extracted = be.energyStorage.extractEnergy(accepted, false);
            if (extracted > 0) {
                remainingOutput -= extracted;
                transferred = true;
                // Resume after the last side that actually received power. Merely
                // advancing by one fixed enum slot is unfair when only a sparse
                // subset of sides has receivers (e.g. DOWN would precede UP on five
                // of six starts). Advancing from the served side gives each hungry
                // receiver the next first chance in cyclic order.
                nextOutputDirectionCursor = (directionIndex + 1) % OUTPUT_DIRECTIONS.length;
            }
        }
        be.outputDirectionCursor = nextOutputDirectionCursor;

        if (transferred || be.charging) {
            be.activeTicks = ACTIVE_DURATION_TICKS;
        }

        if (wasActive != (be.activeTicks > 0) || be.charging != wasCharging) {
            be.setChanged();
            BlockState currentState = be.getBlockState();
            level.sendBlockUpdated(pos, currentState, currentState, 3);
        }
    }

    /** Keep the blockstate and comparator-visible energy state current for all mutation paths. */
    private void onEnergyChanged(int previousEnergy, int currentEnergy) {
        if (previousEnergy == currentEnergy) return;
        setChanged();
        syncHasEnergyState();
    }

    private void syncHasEnergyState() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(EnergyCrystalBlock.FULL)) return;

        boolean hasEnergy = energyStorage.getEnergyStored() > 0;
        if (state.getValue(EnergyCrystalBlock.FULL) != hasEnergy) {
            level.setBlock(worldPosition, state.setValue(EnergyCrystalBlock.FULL, hasEnergy), 3);
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
        outputDirectionCursor = tag.contains("OutputDirectionCursor")
                ? Math.floorMod(tag.getInt("OutputDirectionCursor"), OUTPUT_DIRECTIONS.length)
                : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Energy", energyStorage.serializeNBT());
        tag.putInt("ActiveTicks", activeTicks);
        tag.putBoolean("Charging", charging);
        tag.putInt("OutputDirectionCursor", outputDirectionCursor);
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
        private int availableOutputBudget = 0;
        private long outputBudgetTick = Long.MIN_VALUE;

        ConfigurableEnergyStorage() {
            super(TurretConfig.ENERGY_CRYSTAL_CAPACITY.get(),
                    TurretConfig.ENERGY_CRYSTAL_CAPACITY.get(),
                    TurretConfig.ENERGY_CRYSTAL_MAX_OUTPUT.get() * TICK_INTERVAL, 0);
            applyConfig();
        }

        void applyConfig() {
            int previousEnergy = this.energy;
            this.capacity = getConfiguredCapacity();
            this.maxReceive = this.capacity;
            this.maxExtract = getConfiguredMaxExtract();
            this.energy = Math.max(0, Math.min(this.energy, this.capacity));
            this.availableOutputBudget = Math.min(this.availableOutputBudget, this.maxExtract);
            if (this.energy != previousEnergy) {
                EnergyCrystalBlockEntity.this.onEnergyChanged(previousEnergy, this.energy);
            }
        }

        void setEnergy(int energy) {
            applyConfig();
            int previousEnergy = this.energy;
            this.energy = Math.max(0, Math.min(energy, this.capacity));
            if (this.energy != previousEnergy) {
                EnergyCrystalBlockEntity.this.onEnergyChanged(previousEnergy, this.energy);
            }
        }

        void refreshOutputBudget(long gameTime) {
            int perTickOutput = getConfiguredPerTickOutput();
            int budgetCap = getConfiguredMaxExtract();

            if (outputBudgetTick == Long.MIN_VALUE || gameTime < outputBudgetTick) {
                outputBudgetTick = gameTime;
                availableOutputBudget = Math.min(budgetCap, perTickOutput);
                return;
            }
            if (gameTime == outputBudgetTick) return;

            long elapsed = gameTime - outputBudgetTick;
            long replenished = (long) availableOutputBudget + elapsed * perTickOutput;
            availableOutputBudget = (int) Math.min(budgetCap, replenished);
            outputBudgetTick = gameTime;
        }

        int getAvailableOutputBudget() {
            applyConfig();
            if (level != null && !level.isClientSide) {
                refreshOutputBudget(level.getGameTime());
                return Math.min(availableOutputBudget, energy);
            }
            return Math.min(maxExtract, energy);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            applyConfig();
            int previousEnergy = this.energy;
            int received = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && this.energy != previousEnergy) {
                EnergyCrystalBlockEntity.this.onEnergyChanged(previousEnergy, this.energy);
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            applyConfig();
            if (level == null || level.isClientSide) {
                return super.extractEnergy(maxExtract, simulate);
            }

            refreshOutputBudget(level.getGameTime());
            int allowed = Math.min(Math.max(0, maxExtract), availableOutputBudget);
            if (allowed <= 0) return 0;

            int previousEnergy = this.energy;
            int extracted = super.extractEnergy(allowed, simulate);
            if (!simulate && extracted > 0) {
                availableOutputBudget -= extracted;
                EnergyCrystalBlockEntity.this.onEnergyChanged(previousEnergy, this.energy);
            }
            return extracted;
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
            int loadedEnergy;
            if (nbt instanceof CompoundTag compoundTag) {
                loadedEnergy = compoundTag.getInt("energy");
            } else if (nbt instanceof IntTag intTag) {
                loadedEnergy = intTag.getAsInt();
            } else {
                throw new IllegalArgumentException("Unsupported energy NBT type: " + nbt.getClass().getSimpleName());
            }
            this.energy = Math.max(0, Math.min(loadedEnergy, this.capacity));
        }

        private int getConfiguredCapacity() {
            return TurretConfig.ENERGY_CRYSTAL_CAPACITY.get() * EnergyCrystalBlockEntity.this.getEnergyMultiplier();
        }

        private int getConfiguredMaxExtract() {
            return getConfiguredPerTickOutput() * TICK_INTERVAL;
        }

        private int getConfiguredPerTickOutput() {
            return TurretConfig.ENERGY_CRYSTAL_MAX_OUTPUT.get() * EnergyCrystalBlockEntity.this.getEnergyMultiplier();
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
