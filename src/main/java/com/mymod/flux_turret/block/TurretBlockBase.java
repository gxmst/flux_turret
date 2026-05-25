package com.mymod.flux_turret.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public abstract class TurretBlockBase extends BaseEntityBlock {
    private final RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg;

    protected TurretBlockBase(Properties properties, RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg) {
        super(properties);
        this.blockEntityTypeReg = blockEntityTypeReg;
        this.registerDefaultState(this.defaultBlockState().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(BlockStateProperties.POWERED) != powered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), 3);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityTypeReg.get().create(pos, state);
    }
}
