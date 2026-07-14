package com.mymod.flux_turret.util;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.EnergyStorage;

import java.util.function.IntUnaryOperator;

public class ChargeHelper {
    private ChargeHelper() {}

    /**
     * Compute a comparator output signal (0-15) from an energy fill ratio.
     * Guards against zero/negative capacity and clamps to the valid range.
     */
    public static int energySignal(int stored, int capacity) {
        if (capacity <= 0 || stored <= 0) return 0;
        int signal = (int) ((double) stored / capacity * 15.0);
        return Math.max(0, Math.min(15, signal));
    }

    /** Safely convert a per-unit FE rate into a total recipe/manual charge. */
    public static int totalCharge(int chargePerUnit, int units) {
        if (chargePerUnit <= 0 || units <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (long) chargePerUnit * units);
    }

    public static InteractionResult tryRedstoneCharge(
            Level level, BlockPos pos, BlockState state,
            Player player, ItemStack heldItem,
            EnergyStorage storage, int redstoneCharge, int redstoneBlockCharge) {
        return tryRedstoneCharge(level, pos, state, player, heldItem, storage,
                amount -> receiveFully(storage, amount), redstoneCharge, redstoneBlockCharge);
    }

    public static InteractionResult tryRedstoneCharge(
            Level level, BlockPos pos, BlockState state,
            Player player, ItemStack heldItem,
            EnergyStorage storage, IntUnaryOperator manualReceiver,
            int redstoneCharge, int redstoneBlockCharge) {

        int chargeAmount = 0;
        if (heldItem.is(Items.REDSTONE)) {
            chargeAmount = redstoneCharge;
        } else if (heldItem.is(Items.REDSTONE_BLOCK)) {
            chargeAmount = redstoneBlockCharge;
        }

        if (chargeAmount > 0) {
            int capacity = storage.getMaxEnergyStored();
            int current = storage.getEnergyStored();
            if (current < capacity) {
                boolean usedBlock = heldItem.is(Items.REDSTONE_BLOCK);
                int requested = Math.min(chargeAmount, capacity - current);
                int received = Math.min(requested, Math.max(0, manualReceiver.applyAsInt(requested)));
                if (received > 0) {
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }
                    level.sendBlockUpdated(pos, state, state, 3);
                    float pitch = usedBlock ? 1.8f : 1.5f;
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, pitch);
                    player.displayClientMessage(
                            Component.translatable("message.flux_turret.charge_success",
                                    received, storage.getEnergyStored(), capacity)
                                .withStyle(net.minecraft.ChatFormatting.AQUA),
                            true);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return null;
    }

    private static int receiveFully(EnergyStorage storage, int amount) {
        int receivedTotal = 0;
        int remaining = Math.max(0, amount);
        while (remaining > 0) {
            int received = storage.receiveEnergy(remaining, false);
            if (received <= 0) break;
            receivedTotal += received;
            remaining -= received;
        }
        return receivedTotal;
    }
}
