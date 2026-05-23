package com.mymod.flux_turret.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public abstract class TurretBlockBase extends BaseEntityBlock {
    private final RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg;

    protected TurretBlockBase(Properties properties, RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg) {
        super(properties);
        this.blockEntityTypeReg = blockEntityTypeReg;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityTypeReg.get().create(pos, state);
    }
}
