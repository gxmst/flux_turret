package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import com.mymod.flux_turret.item.TurretUpgradeModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
    private static final String PORTABLE_DROP_SNAPSHOT = "flux_turret:portable_drop_snapshot";

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
                if (context.getPlayer() != null && level.isClientSide) {
                    context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.flux_turret.placement_blocked", checkPos.toShortString()), true);
                }
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
            if (placer instanceof Player player) cannon.claimIfUnowned(player);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide) {
            CannonPart part = state.getValue(PART);
            Direction facing = state.getValue(FACING);
            BlockPos corePos = part == CannonPart.BACK_LEFT ? pos : part.getCorePos(pos, facing);

            // Draining the core first makes recursive multi-block teardown idempotent.
            TurretUpgradeModuleItem.dropInstalledModules(level, corePos, level.getBlockEntity(corePos));

            if (part == CannonPart.BACK_LEFT) {
                // Core removed: tear down the generated parts without creating extra drops.
                for (CannonPart p : CannonPart.values()) {
                    if (p == CannonPart.BACK_LEFT) continue;
                    BlockPos partPos = p.offset(pos, facing);
                    BlockState partState = level.getBlockState(partPos);
                    if (partState.getBlock() == this) {
                        level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 18);
                    }
                }
            } else {
                // Part broken: find core and destroy entire structure
                BlockState coreState = level.getBlockState(corePos);
                if (coreState.getBlock() == this && coreState.hasProperty(PART)
                        && coreState.getValue(PART) == CannonPart.BACK_LEFT) {
                    level.setBlock(corePos, Blocks.AIR.defaultBlockState(), 3);
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
            InteractionResult upgradeResult = TurretUpgradeModuleItem.tryHandleInteraction(level, corePos, player, hand, be);
            if (upgradeResult != InteractionResult.PASS) {
                return upgradeResult;
            }

            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.mymod.flux_turret.menu.TurretInspectorMenu.open(serverPlayer, cannon);
                }
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Player loot is evaluated after onRemove. Removing a generated part tears down
        // the core first, so preserve the core's portable data on the captured part BE
        // that vanilla later supplies to getDrops. This keeps the normal harvest and
        // creative-mode rules instead of spawning an unconditional manual drop.
        if (!level.isClientSide && !player.isCreative()
                && state.hasProperty(PART) && state.hasProperty(FACING)
                && state.getValue(PART) != CannonPart.BACK_LEFT
                && level.getBlockEntity(pos) instanceof GrandCannonBlockEntity partEntity) {
            BlockPos corePos = state.getValue(PART).getCorePos(pos, state.getValue(FACING));
            if (level.getBlockEntity(corePos) instanceof GrandCannonBlockEntity coreEntity) {
                CompoundTag snapshot = new CompoundTag();
                coreEntity.savePortableData(snapshot);
                partEntity.getPersistentData().put(PORTABLE_DROP_SNAPSHOT, snapshot);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        GrandCannonBlockEntity cannon = blockEntity instanceof GrandCannonBlockEntity direct ? direct : null;
        CompoundTag portableSnapshot = cannon != null
                && cannon.getPersistentData().contains(PORTABLE_DROP_SNAPSHOT, Tag.TAG_COMPOUND)
                ? cannon.getPersistentData().getCompound(PORTABLE_DROP_SNAPSHOT).copy()
                : null;
        if (portableSnapshot == null && cannon != null
                && state.hasProperty(PART) && state.hasProperty(FACING)
                && state.getValue(PART) != CannonPart.BACK_LEFT && cannon.getLevel() != null) {
            BlockPos corePos = state.getValue(PART).getCorePos(cannon.getBlockPos(), state.getValue(FACING));
            if (cannon.getLevel().getBlockEntity(corePos) instanceof GrandCannonBlockEntity core) cannon = core;
        }
        java.util.List<net.minecraft.world.item.ItemStack> drops = super.getDrops(state, builder);
        if (cannon != null) {
            for (net.minecraft.world.item.ItemStack stack : drops) {
                if (stack.is(this.asItem())) {
                    CompoundTag blockEntityTag = stack.getOrCreateTagElement("BlockEntityTag");
                    if (portableSnapshot != null) blockEntityTag.merge(portableSnapshot.copy());
                    else cannon.savePortableData(blockEntityTag);
                }
            }
        }
        return drops;
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
