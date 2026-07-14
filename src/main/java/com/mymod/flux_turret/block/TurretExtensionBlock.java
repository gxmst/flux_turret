package com.mymod.flux_turret.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Invisible reserved cells above tall turrets; interaction and teardown proxy to the base. */
public class TurretExtensionBlock extends Block {
    public static final IntegerProperty OFFSET = IntegerProperty.create("offset", 1, 2);
    private static final java.util.Map<Level, Set<BlockPos>> REMOVING_CORES = new IdentityHashMap<>();

    public TurretExtensionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OFFSET, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OFFSET);
    }

    public BlockPos getCorePos(BlockState state, BlockPos extensionPos) {
        return TurretExtensionRules.corePosForOffset(extensionPos, state.getValue(OFFSET));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos corePos = getCorePos(state, pos);
        BlockState coreState = level.getBlockState(corePos);
        if (coreState.getBlock() instanceof TurretBlockBase) {
            return coreState.use(level, player, hand,
                    new BlockHitResult(hit.getLocation(), hit.getDirection(), corePos, hit.isInside()));
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        playerWillDestroy(level, pos, state, player);
        boolean removedCore = false;
        if (!level.isClientSide) {
            BlockPos corePos = getCorePos(state, pos);
            if (level.getBlockState(corePos).getBlock() instanceof TurretBlockBase) {
                // Proxy the actual destruction before this reservation cell is gone.
                // Creative mode and failed harvests keep vanilla's no-drop behavior.
                removedCore = level.destroyBlock(corePos,
                        willHarvest && !player.isCreative(), player);
            }
        }
        boolean removedCell = level.setBlock(pos, fluid.createLegacyBlock(),
                level.isClientSide ? 11 : 3);
        return removedCore || removedCell;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide) {
            BlockPos corePos = getCorePos(state, pos);
            Set<BlockPos> removing = REMOVING_CORES.getOrDefault(level, Collections.emptySet());
            if (!removing.contains(corePos) && level.getBlockState(corePos).getBlock() instanceof TurretBlockBase) {
                level.destroyBlock(corePos, true);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    public static void placeExtensions(Level level, BlockPos corePos, int height) {
        for (int offset = 1; offset < height; offset++) {
            level.setBlock(corePos.above(offset),
                    com.mymod.flux_turret.ModRegistry.TURRET_EXTENSION_BLOCK.get().defaultBlockState()
                            .setValue(OFFSET, offset), 3);
        }
    }

    public static void removeExtensions(Level level, BlockPos corePos, int height) {
        Set<BlockPos> removing = REMOVING_CORES.computeIfAbsent(level, ignored -> new java.util.HashSet<>());
        removing.add(corePos.immutable());
        try {
            for (int offset = 1; offset < height; offset++) {
                BlockPos extensionPos = corePos.above(offset);
                BlockState extension = level.getBlockState(extensionPos);
                if (extension.getBlock() instanceof TurretExtensionBlock
                        && getStaticCorePos(extension, extensionPos).equals(corePos)) {
                    level.removeBlock(extensionPos, false);
                }
            }
        } finally {
            removing.remove(corePos);
            if (removing.isEmpty()) REMOVING_CORES.remove(level);
        }
    }

    private static BlockPos getStaticCorePos(BlockState state, BlockPos pos) {
        return pos.below(state.getValue(OFFSET));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TurretExtensionRules.outlineShape();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return TurretExtensionRules.collisionShape();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // Moving only the invisible reservation cell would tear down the core and
        // leave an orphan extension at the piston destination.
        return TurretExtensionRules.pistonReaction();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState core = level.getBlockState(getCorePos(state, pos));
        return new ItemStack(core.getBlock());
    }
}

/** Pure extension-cell rules that can be unit tested without bootstrapping registries. */
final class TurretExtensionRules {
    private static final VoxelShape OUTLINE_SHAPE = Shapes.block();

    private TurretExtensionRules() {
    }

    static BlockPos corePosForOffset(BlockPos extensionPos, int offset) {
        return extensionPos.below(offset);
    }

    static VoxelShape outlineShape() {
        return OUTLINE_SHAPE;
    }

    static VoxelShape collisionShape() {
        return Shapes.empty();
    }

    static PushReaction pistonReaction() {
        return PushReaction.BLOCK;
    }
}
