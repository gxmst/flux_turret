package com.mymod.flux_turret.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class EmptyCrystalItem extends BlockItem {
    public EmptyCrystalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public String getDescriptionId() {
        return "item.flux_turret.empty_crystal";
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
    }
}
