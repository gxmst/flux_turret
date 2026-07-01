package com.mymod.flux_turret.item;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
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
        int max = getMaxEnergyStored(stack);
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
            Tag energyTag = blockEntityTag.get("Energy");
            if (energyTag instanceof IntTag intTag) {
                return clampEnergy(stack, intTag.getAsInt());
            }
            if (energyTag instanceof CompoundTag compoundTag) {
                return clampEnergy(stack, compoundTag.getInt("energy"));
            }
        }
        // No NBT = freshly smelted = full charge
        return getMaxEnergyStored(stack);
    }

    public static boolean hasEnergyNBT(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        return blockEntityTag != null && blockEntityTag.contains("Energy");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getEnergyStored(stack) > 0;
    }

    public static ItemStack createChargedStack(int energy) {
        return createChargedStack(ModRegistry.ENERGY_CRYSTAL_ITEM.get(), energy);
    }

    public static ItemStack createChargedStack(Item item, int energy) {
        ItemStack stack = new ItemStack(item);
        setEnergyStored(stack, energy);
        return stack;
    }

    public static void setEnergyStored(ItemStack stack, int energy) {
        CompoundTag blockEntityTag = stack.getOrCreateTagElement("BlockEntityTag");
        blockEntityTag.put("Energy", IntTag.valueOf(clampEnergy(stack, energy)));
    }

    public static int getMaxEnergyStored(ItemStack stack) {
        if (stack.getItem() instanceof EnergyCrystalItem crystalItem
                && crystalItem.getBlock() instanceof com.mymod.flux_turret.block.EnergyCrystalBlock crystalBlock) {
            return TurretConfig.ENERGY_CRYSTAL_CAPACITY.get() * crystalBlock.getEnergyMultiplier();
        }
        return TurretConfig.ENERGY_CRYSTAL_CAPACITY.get();
    }

    private static int clampEnergy(ItemStack stack, int energy) {
        return Math.max(0, Math.min(energy, getMaxEnergyStored(stack)));
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            int energy = getEnergyStored(stack);
            be.setEnergyStored(energy);
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            return true;
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}
