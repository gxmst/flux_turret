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

    public static InteractionResult tryRedstoneCharge(
            Level level, BlockPos pos, BlockState state,
            Player player, ItemStack heldItem,
            EnergyStorage storage, int redstoneCharge, int redstoneBlockCharge) {

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
                int received = storage.receiveEnergy(chargeAmount, false);
                if (received > 0) {
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }
                    level.sendBlockUpdated(pos, state, state, 3);
                    float pitch = heldItem.is(Items.REDSTONE_BLOCK) ? 1.8f : 1.5f;
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
}
