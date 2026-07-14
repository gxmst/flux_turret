package com.mymod.flux_turret.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla-compatible chest instance used while a Psychic Beacon reward is
 * inside its owner grace period. It deliberately keeps the vanilla chest block
 * entity type, so it renders, ticks, saves, and remains compatible with old
 * worlds exactly like a normal chest.
 *
 * <p>The subclass only closes automation paths during the grace period. Player
 * access remains governed by the Forge interaction events in {@code FluxTurretMod}.
 * On world reload the vanilla id creates a normal {@link ChestBlockEntity}; the
 * chunk-load migration replaces marked reward chests with this subclass before
 * the next server tick.</p>
 */
public final class ProtectedRewardChestBlockEntity extends ChestBlockEntity {
    private LazyOptional<IItemHandler> guardedItemHandler = createGuardedItemHandler();

    public ProtectedRewardChestBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CHEST, pos, state);
    }

    private boolean protectionActive() {
        Level level = getLevel();
        return level != null && PsychicBeaconBlockEntity.isRewardChestProtectionActive(level, this);
    }

    @Override
    public boolean canTakeItem(Container destination, int slot, ItemStack stack) {
        return !protectionActive();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Keep vanilla hoppers from filling every reward slot during the claim
        // window. The reward chest is intentionally read-only until protection
        // expires; its owner/team can still remove rewards through the menu.
        return !protectionActive();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && !isRemoved()) {
            return guardedItemHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    private LazyOptional<IItemHandler> createGuardedItemHandler() {
        return LazyOptional.of(() -> new IItemHandler() {
            private IItemHandler delegate() {
                return ProtectedRewardChestBlockEntity.super
                        .getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                        .orElseThrow(() -> new IllegalStateException("Vanilla chest item handler unavailable"));
            }

            @Override
            public int getSlots() {
                return delegate().getSlots();
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                return delegate().getStackInSlot(slot);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return protectionActive() ? stack : delegate().insertItem(slot, stack, simulate);
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return protectionActive() ? ItemStack.EMPTY : delegate().extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return delegate().getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return !protectionActive() && delegate().isItemValid(slot, stack);
            }

        });
    }

    @Override
    public void invalidateCaps() {
        guardedItemHandler.invalidate();
        super.invalidateCaps();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        guardedItemHandler = createGuardedItemHandler();
    }

    /** Replace a reloaded vanilla reward chest without losing items or Forge data. */
    public static void restoreProtectionWrapper(
            Level level, LevelChunk chunk, ChestBlockEntity chest) {
        if (level.isClientSide
                || chest instanceof ProtectedRewardChestBlockEntity
                || !PsychicBeaconBlockEntity.isRewardChestProtectionActive(level, chest)) {
            return;
        }

        CompoundTag saved = chest.saveWithFullMetadata();
        ProtectedRewardChestBlockEntity replacement =
                new ProtectedRewardChestBlockEntity(chest.getBlockPos(), chest.getBlockState());
        replacement.load(saved);
        // ChunkEvent.Load fires after vanilla registered this chunk's block
        // entities. Replace directly in that already-loading chunk so no hopper
        // tick can run between load and protection restoration.
        chunk.addAndRegisterBlockEntity(replacement);
    }
}
