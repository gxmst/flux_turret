package com.mymod.flux_turret.item;

import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EnergyCrystalItem extends BlockItem {
    public EnergyCrystalItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int stored = getEnergyStored(stack);
        int max = EnergyCrystalBlockEntity.CAPACITY;
        ChatFormatting color = stored > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
        tooltip.add(Component.literal(String.format("Energy: %d / %d FE", stored, max))
                .withStyle(color));
    }

    /**
     * Get energy from item NBT. If no BlockEntityTag/Energy tag exists,
     * treat as fully charged (freshly smelted crystal).
     */
    public static int getEnergyStored(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        if (blockEntityTag != null && blockEntityTag.contains("Energy")) {
            return blockEntityTag.getCompound("Energy").getInt("energy");
        }
        // No NBT = freshly smelted = full charge
        return EnergyCrystalBlockEntity.CAPACITY;
    }

    public static boolean hasEnergyNBT(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        return blockEntityTag != null && blockEntityTag.contains("Energy");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getEnergyStored(stack) > 0;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            int energy = getEnergyStored(stack);
            // Set the block entity energy to match the item
            be.getEnergyStorage().extractEnergy(be.getEnergyStorage().getEnergyStored(), false);
            be.getEnergyStorage().receiveEnergy(energy, false);
            be.setChanged();
        }
        return result;
    }
}
