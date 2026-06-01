package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.util.ChargeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PsychicBeaconBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 32, 16);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public PsychicBeaconBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) {
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.literal("\u00a7c\u4e0a\u65b9\u7a7a\u95f4\u4e0d\u8db3\uff0c\u65e0\u6cd5\u653e\u7f6e\u5fc3\u7075\u4fe1\u6807\uff01\u9700\u89812\u683c\u9ad8\u7684\u7a7a\u95f4\u3002"), true);
            }
            return null;
        }
        return defaultBlockState();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PsychicBeaconBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.PSYCHIC_BEACON_BE.get(), PsychicBeaconBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof PsychicBeaconBlockEntity beacon) {
            ItemStack heldItem = player.getItemInHand(hand);

            InteractionResult chargeResult = ChargeHelper.tryRedstoneCharge(
                    level, pos, state, player, heldItem, beacon.getEnergyStorage(),
                    TurretConfig.PSYCHIC_BEACON_REDSTONE_CHARGE.get(),
                    TurretConfig.PSYCHIC_BEACON_REDSTONE_BLOCK_CHARGE.get());
            if (chargeResult != null) {
                beacon.setChanged();
                return chargeResult;
            }

            NetworkHooks.openScreen((ServerPlayer) player, beacon, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PsychicBeaconBlockEntity beacon) {
            int stored = beacon.getEnergyStorage().getEnergyStored();
            int max = beacon.getEnergyStorage().getMaxEnergyStored();
            return (int) ((double) stored / max * 15);
        }
        return 0;
    }
}
