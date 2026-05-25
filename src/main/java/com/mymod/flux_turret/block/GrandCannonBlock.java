package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class GrandCannonBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<CannonPart> PART = EnumProperty.create("part", CannonPart.class);

    public GrandCannonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, CannonPart.BACK_LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        // Check if all 4 positions are available
        for (CannonPart part : CannonPart.values()) {
            BlockPos checkPos = part.offset(pos, facing);
            if (!level.getBlockState(checkPos).canBeReplaced(context)) {
                return null;
            }
        }

        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, CannonPart.BACK_LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);

        // Auto-generate all other parts
        for (CannonPart part : CannonPart.values()) {
            if (part == CannonPart.BACK_LEFT) continue;
            BlockPos partPos = part.offset(pos, facing);
            level.setBlock(partPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, part), 3);
        }

        // Notify the core block entity
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GrandCannonBlockEntity cannon) {
            cannon.setFormed(true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            CannonPart part = state.getValue(PART);
            Direction facing = state.getValue(FACING);

            if (part == CannonPart.BACK_LEFT) {
                // Core broken: remove all parts, drop item at core
                for (CannonPart p : CannonPart.values()) {
                    if (p == CannonPart.BACK_LEFT) continue;
                    BlockPos partPos = p.offset(pos, facing);
                    BlockState partState = level.getBlockState(partPos);
                    if (partState.getBlock() == this) {
                        // Use setBlock with AIR to avoid triggering onRemove recursively
                        level.setBlock(partPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
                    }
                }
                // Drop item at core position
                if (!level.isClientSide) {
                    net.minecraft.world.item.ItemStack dropStack = new net.minecraft.world.item.ItemStack(this);
                    net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                            level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dropStack);
                    level.addFreshEntity(itemEntity);
                }
            } else {
                // Part broken: find core and destroy entire structure
                BlockPos corePos = part.getCorePos(pos, facing);
                BlockState coreState = level.getBlockState(corePos);
                if (coreState.getBlock() == this && coreState.hasProperty(PART)
                        && coreState.getValue(PART) == CannonPart.BACK_LEFT) {
                    Direction coreFacing = coreState.getValue(FACING);
                    // Remove all other parts first (avoid recursion)
                    for (CannonPart p : CannonPart.values()) {
                        if (p == CannonPart.BACK_LEFT) continue;
                        BlockPos pPos = p.offset(corePos, coreFacing);
                        if (!pPos.equals(pos)) {
                            BlockState ps = level.getBlockState(pPos);
                            if (ps.getBlock() == this) {
                                level.setBlock(pPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
                            }
                        }
                    }
                    // Remove core and drop item
                    level.setBlock(corePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
                    if (!level.isClientSide) {
                        net.minecraft.world.item.ItemStack dropStack = new net.minecraft.world.item.ItemStack(this);
                        net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                level, corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5, dropStack);
                        level.addFreshEntity(itemEntity);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // Redirect interaction to core block entity
        CannonPart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        BlockPos corePos = part == CannonPart.BACK_LEFT ? pos : part.getCorePos(pos, facing);

        BlockEntity be = level.getBlockEntity(corePos);
        if (be instanceof GrandCannonBlockEntity cannon) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "Energy: " + cannon.getEnergyStored() + " / " + cannon.getEnergyCapacity()
                                    + " | Formed: " + cannon.isFormed()),
                    true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == CannonPart.BACK_LEFT
                ? RenderShape.ENTITYBLOCK_ANIMATED
                : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // All 4 parts create BlockEntity so non-core blocks can proxy energy to the core
        return ModRegistry.GRAND_CANNON_BE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != CannonPart.BACK_LEFT) return null;
        return createTickerHelper(type, ModRegistry.GRAND_CANNON_BE.get(), GrandCannonBlockEntity::tick);
    }

    /**
     * 2x2x1 multi-block structure parts.
     * Structure: 2 blocks wide (perpendicular to facing) x 2 blocks deep (along facing) x 1 block tall.
     * Core block is BACK_LEFT. The cannon fires in the FACING direction.
     *
     * Layout (looking down, facing NORTH):
     *   FRONT_LEFT  FRONT_RIGHT
     *   BACK_LEFT   BACK_RIGHT   <- core
     */
    public enum CannonPart implements net.minecraft.util.StringRepresentable {
        BACK_LEFT(0, 0),       // Core block
        BACK_RIGHT(0, 1),      // Right of core (perpendicular)
        FRONT_LEFT(1, 0),      // Forward from core
        FRONT_RIGHT(1, 1);     // Forward + right

        // forwardOffset: blocks in the facing direction
        // rightOffset: blocks to the right (clockwise from facing)
        public final int forwardOffset;
        public final int rightOffset;

        CannonPart(int forwardOffset, int rightOffset) {
            this.forwardOffset = forwardOffset;
            this.rightOffset = rightOffset;
        }

        public boolean isCore() {
            return this == BACK_LEFT;
        }

        /**
         * Get the world position of this part given the core position and facing direction.
         */
        public BlockPos offset(BlockPos corePos, Direction facing) {
            Direction right = facing.getClockWise();
            return corePos
                    .relative(facing, forwardOffset)
                    .relative(right, rightOffset);
        }

        /**
         * Get the core position from this part's world position and facing direction.
         */
        public BlockPos getCorePos(BlockPos partPos, Direction facing) {
            Direction right = facing.getClockWise();
            return partPos
                    .relative(facing.getOpposite(), forwardOffset)
                    .relative(right.getOpposite(), rightOffset);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
