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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EnergyCrystalItem extends BlockItem {
    static final String ENERGY_TAG = "Energy";
    static final String ENERGY_DATA_VERSION_TAG = "FluxEnergyDataVersion";
    static final int CURRENT_ENERGY_DATA_VERSION = EnergyCrystalDataRules.CURRENT_VERSION;

    public EnergyCrystalItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        boolean legacyData = isLegacyEnergyData(stack);
        int stored = getEnergyStored(stack);
        int max = getMaxEnergyStored(stack);
        ChatFormatting color = stored > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY;
        tooltip.add(Component.translatable("tooltip.flux_turret.energy_stored", stored, max)
                .withStyle(color));

        int maxOutput = getMaxOutput(stack);
        tooltip.add(Component.translatable("tooltip.flux_turret.energy_crystal_output", maxOutput)
                .withStyle(ChatFormatting.DARK_AQUA));
        if (stored > 0 && maxOutput > 0) {
            int fullLoadSeconds = Math.max(1, (int) Math.ceil(stored / (maxOutput * 20.0D)));
            tooltip.add(Component.translatable("tooltip.flux_turret.energy_crystal_runtime", fullLoadSeconds)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (legacyData) {
            tooltip.add(Component.translatable("tooltip.flux_turret.energy_crystal_legacy_preserved")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * Read stored FE without destroying the legacy meaning of an untagged stack.
     * Versions through 1.5 treated untagged energy crystals as fully charged, so
     * those items retain one full configured charge. Every newly-created result
     * is explicitly versioned, including zero-energy results.
     */
    public static int getEnergyStored(ItemStack stack) {
        return readStoredEnergy(stack.getTagElement("BlockEntityTag"), getMaxEnergyStored(stack));
    }

    static int readStoredEnergy(@Nullable CompoundTag blockEntityTag, int maxEnergy) {
        boolean hasEnergyField = blockEntityTag != null && blockEntityTag.contains(ENERGY_TAG);
        Integer explicitEnergy = null;
        if (blockEntityTag != null && blockEntityTag.contains(ENERGY_TAG)) {
            Tag energyTag = blockEntityTag.get(ENERGY_TAG);
            if (energyTag instanceof IntTag intTag) {
                explicitEnergy = intTag.getAsInt();
            } else if (energyTag instanceof CompoundTag compoundTag) {
                explicitEnergy = compoundTag.getInt("energy");
            }
        }
        int dataVersion = blockEntityTag == null ? 0 : blockEntityTag.getInt(ENERGY_DATA_VERSION_TAG);
        return EnergyCrystalDataRules.resolveStoredEnergy(
                hasEnergyField, explicitEnergy, dataVersion, maxEnergy);
    }

    public static boolean hasEnergyNBT(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        return blockEntityTag != null && blockEntityTag.contains(ENERGY_TAG);
    }

    public static boolean isLegacyEnergyData(ItemStack stack) {
        return isLegacyEnergyData(stack.getTagElement("BlockEntityTag"));
    }

    static boolean isLegacyEnergyData(@Nullable CompoundTag blockEntityTag) {
        return EnergyCrystalDataRules.isLegacy(
                blockEntityTag == null ? 0 : blockEntityTag.getInt(ENERGY_DATA_VERSION_TAG));
    }

    /**
     * Persist the interpreted legacy value exactly once. This handles both the
     * formerly implicit full charge and old explicit partial-charge NBT.
     */
    public static boolean migrateLegacyEnergyData(ItemStack stack) {
        CompoundTag existing = stack.getTagElement("BlockEntityTag");
        if (!isLegacyEnergyData(existing)) return false;
        int stored = readStoredEnergy(existing, getMaxEnergyStored(stack));
        writeEnergyData(stack.getOrCreateTagElement("BlockEntityTag"), stored,
                getMaxEnergyStored(stack));
        return true;
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
        writeEnergyData(blockEntityTag, energy, getMaxEnergyStored(stack));
    }

    static void writeEnergyData(CompoundTag blockEntityTag, int energy, int maxEnergy) {
        blockEntityTag.put(ENERGY_TAG, IntTag.valueOf(clampEnergy(energy, Math.max(0, maxEnergy))));
        blockEntityTag.putInt(ENERGY_DATA_VERSION_TAG, CURRENT_ENERGY_DATA_VERSION);
    }

    public static int getMaxEnergyStored(ItemStack stack) {
        return TurretConfig.ENERGY_CRYSTAL_CAPACITY.get() * getEnergyMultiplier(stack);
    }

    public static int getMaxOutput(ItemStack stack) {
        return TurretConfig.ENERGY_CRYSTAL_MAX_OUTPUT.get() * getEnergyMultiplier(stack);
    }

    private static int getEnergyMultiplier(ItemStack stack) {
        if (stack.getItem() instanceof EnergyCrystalItem crystalItem
                && crystalItem.getBlock() instanceof com.mymod.flux_turret.block.EnergyCrystalBlock crystalBlock) {
            return crystalBlock.getEnergyMultiplier();
        }
        return 1;
    }

    private static int clampEnergy(int energy, int maxEnergy) {
        return Math.max(0, Math.min(energy, maxEnergy));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide) migrateLegacyEnergyData(stack);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            migrateLegacyEnergyData(stack);
            int energy = getEnergyStored(stack);
            be.setEnergyStored(energy);
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            return true;
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}
