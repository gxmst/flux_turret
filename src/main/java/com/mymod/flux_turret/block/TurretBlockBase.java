package com.mymod.flux_turret.block;

import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import com.mymod.flux_turret.item.TurretUpgradeModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public abstract class TurretBlockBase extends BaseEntityBlock {
    private final RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg;
    private final BlockEntityTicker<?> serverTicker;
    private final VoxelShape shape;
    private final int heightBlocks;

    protected <E extends BlockEntity> TurretBlockBase(Properties properties,
            RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg,
            BlockEntityTicker<E> serverTicker) {
        this(properties, blockEntityTypeReg, 1, serverTicker);
    }

    protected <E extends BlockEntity> TurretBlockBase(Properties properties,
            RegistryObject<? extends BlockEntityType<?>> blockEntityTypeReg,
            int heightBlocks, BlockEntityTicker<E> serverTicker) {
        super(properties);
        this.blockEntityTypeReg = blockEntityTypeReg;
        this.serverTicker = serverTicker;
        this.heightBlocks = heightBlocks;
        this.shape = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, heightBlocks * 16, 16);
        this.registerDefaultState(this.defaultBlockState().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (int i = 1; i < heightBlocks; i++) {
            BlockPos above = pos.above(i);
            if (!level.getBlockState(above).isAir() && !level.getBlockState(above).canBeReplaced()) {
                return null;
            }
        }
        return defaultBlockState();
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TurretBlockEntityBase) {
            InteractionResult result = TurretUpgradeModuleItem.tryInstall(level, pos, player, hand, be);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityTypeReg.get().create(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (serverTicker == null) return null;
        return createTickerHelper(type, (BlockEntityType) blockEntityTypeReg.get(),
                (BlockEntityTicker) serverTicker);
    }
}
