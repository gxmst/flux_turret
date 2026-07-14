package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.util.ChargeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PsychicBeaconBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 16, 16);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.create("half", DoubleBlockHalf.class);

    public PsychicBeaconBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT, HALF);
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
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced())) {
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.flux_turret.beacon_no_space")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return null;
        }
        return defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (!level.isClientSide && placer instanceof Player player && !(player instanceof FakePlayer)
                && level.getBlockEntity(pos) instanceof PsychicBeaconBlockEntity beacon) {
            beacon.claimIfUnowned(player);
        }
    }

    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        // The upper half is a visual extension and never owns portable state. If
        // a player breaks it, playerWillDestroy explicitly drops the lower half.
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return java.util.List.of();
        BlockEntity blockEntity = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        java.util.List<ItemStack> drops = super.getDrops(state, builder);
        if (blockEntity instanceof PsychicBeaconBlockEntity beacon) {
            for (ItemStack stack : drops) {
                if (stack.is(this.asItem())) {
                    beacon.savePortableData(stack.getOrCreateTagElement("BlockEntityTag"));
                }
            }
        }
        return drops;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return new PsychicBeaconBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return createTickerHelper(type, ModRegistry.PSYCHIC_BEACON_BE.get(), PsychicBeaconBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos beaconPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockState beaconState = level.getBlockState(beaconPos);
        if (level.getBlockEntity(beaconPos) instanceof PsychicBeaconBlockEntity beacon) {
            if (!beacon.canPlayerConfigure(player)) {
                player.displayClientMessage(Component.translatable("message.flux_turret.beacon_access_denied")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            ItemStack heldItem = player.getItemInHand(hand);

            if (beacon.getBeaconState() == PsychicBeaconBlockEntity.STATE_FAILED
                    && heldItem.is(Items.AMETHYST_SHARD)) {
                int repairCost = TurretConfig.PSYCHIC_BEACON_REPAIR_SHARDS.get();
                if (heldItem.getCount() < repairCost) {
                    player.displayClientMessage(Component.translatable(
                                    "message.flux_turret.beacon_repair_requires", repairCost)
                            .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                    return InteractionResult.CONSUME;
                }
                if (beacon.repairFailedBeacon()) {
                    if (!player.getAbilities().instabuild) heldItem.shrink(repairCost);
                    player.displayClientMessage(Component.translatable("message.flux_turret.beacon_repaired")
                            .withStyle(net.minecraft.ChatFormatting.GREEN), true);
                    return InteractionResult.CONSUME;
                }
            }

            InteractionResult chargeResult = ChargeHelper.tryRedstoneCharge(
                    level, beaconPos, beaconState, player, heldItem, beacon.getEnergyStorage(),
                    beacon::receiveManualEnergy,
                    TurretConfig.PSYCHIC_BEACON_REDSTONE_CHARGE.get(),
                    TurretConfig.PSYCHIC_BEACON_REDSTONE_BLOCK_CHARGE.get());
            if (chargeResult != null) {
                return chargeResult;
            }

            NetworkHooks.openScreen((ServerPlayer) player, beacon, beaconPos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)
                && (!neighborState.is(this) || neighborState.getValue(HALF) == half)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);
        if (otherState.is(this) && otherState.getValue(HALF) != half) {
            if (!level.isClientSide && half == DoubleBlockHalf.UPPER
                    && !player.getAbilities().instabuild) {
                BlockEntity lowerBlockEntity = level.getBlockEntity(otherPos);
                Block.dropResources(otherState, level, otherPos, lowerBlockEntity,
                        player, player.getMainHandItem());
            }
            level.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockPos beaconPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.getBlockEntity(beaconPos) instanceof PsychicBeaconBlockEntity beacon) {
            return ChargeHelper.energySignal(
                    beacon.getEnergyStorage().getEnergyStored(),
                    beacon.getEnergyStorage().getMaxEnergyStored());
        }
        return 0;
    }
}
