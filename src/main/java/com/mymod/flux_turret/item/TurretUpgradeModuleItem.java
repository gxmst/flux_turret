package com.mymod.flux_turret.item;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TurretUpgradeModuleItem extends Item {
    private final TurretUpgradeType upgradeType;

    public TurretUpgradeModuleItem(TurretUpgradeType upgradeType, Properties properties) {
        super(properties);
        this.upgradeType = upgradeType;
    }

    public TurretUpgradeType getUpgradeType() {
        return upgradeType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(upgradeType.getDescriptionKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_turret.upgrade_module.install").withStyle(ChatFormatting.DARK_AQUA));
    }

    public static InteractionResult tryInstall(Level level, BlockPos pos, Player player, InteractionHand hand, BlockEntity blockEntity) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof TurretUpgradeModuleItem module)) {
            return InteractionResult.PASS;
        }

        if (!(blockEntity instanceof TurretBlockEntityBase turret)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        TurretUpgradeType type = module.getUpgradeType();
        if (!turret.canInstallUpgrade(type)) {
            player.displayClientMessage(Component.translatable("message.flux_turret.upgrade_incompatible"), true);
            return InteractionResult.CONSUME;
        }
        if (turret.hasUpgrade(type)) {
            player.displayClientMessage(Component.translatable("message.flux_turret.upgrade_already_installed"), true);
            return InteractionResult.CONSUME;
        }

        turret.installUpgrade(type);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.65f, 1.45f);
        player.displayClientMessage(Component.translatable("message.flux_turret.upgrade_installed",
                Component.translatable(type.getDescriptionKey())), true);
        return InteractionResult.CONSUME;
    }

    /** Handles the two module gestures shared by every turret block. */
    public static InteractionResult tryHandleInteraction(Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, BlockEntity blockEntity) {
        InteractionResult recovery = tryRecover(level, pos, player, hand, blockEntity);
        return recovery != InteractionResult.PASS
                ? recovery
                : tryInstall(level, pos, player, hand, blockEntity);
    }

    /**
     * Sneak + empty-hand removes all installed modules. Inventory insertion keeps
     * the modules' normal stacking behavior; any module that does not fit is dropped
     * beside the player.
     */
    public static InteractionResult tryRecover(Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockEntity blockEntity) {
        if (!player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()
                || !(blockEntity instanceof TurretBlockEntityBase turret)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        List<TurretUpgradeType> removed = turret.removeAllUpgrades();
        if (removed.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flux_turret.upgrade_none_installed"), true);
            return InteractionResult.CONSUME;
        }

        for (TurretUpgradeType type : removed) {
            ItemStack moduleStack = createModuleStack(type);
            if (!player.addItem(moduleStack)) {
                player.drop(moduleStack, false);
            }
        }

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.65f, 0.9f);
        player.displayClientMessage(Component.translatable("message.flux_turret.upgrade_recovered", removed.size()), true);
        return InteractionResult.CONSUME;
    }

    /** Drops and clears a turret's modules before its block entity is discarded. */
    public static int dropInstalledModules(Level level, BlockPos dropPos, @Nullable BlockEntity blockEntity) {
        if (level.isClientSide || !(blockEntity instanceof TurretBlockEntityBase turret)) {
            return 0;
        }

        List<TurretUpgradeType> removed = turret.removeAllUpgrades();
        for (TurretUpgradeType type : removed) {
            Block.popResource(level, dropPos, createModuleStack(type));
        }
        return removed.size();
    }

    private static ItemStack createModuleStack(TurretUpgradeType type) {
        Item moduleItem = switch (type) {
            case ARMOR_PIERCING_ROUNDS -> ModRegistry.ARMOR_PIERCING_ROUNDS_MODULE.get();
            case FIRE_ROUNDS -> ModRegistry.FIRE_ROUNDS_MODULE.get();
            case SLOW_ROUNDS -> ModRegistry.SLOW_ROUNDS_MODULE.get();
            case CHAIN_JUMP -> ModRegistry.CHAIN_JUMP_MODULE.get();
            case EMP_SLOW -> ModRegistry.EMP_SLOW_MODULE.get();
            case OVERLOAD_BURST -> ModRegistry.OVERLOAD_BURST_MODULE.get();
            case FOCUSED_BEAM -> ModRegistry.FOCUSED_BEAM_MODULE.get();
            case REFRACTION_BEAM -> ModRegistry.REFRACTION_BEAM_MODULE.get();
            case REMOTE_SUPPORT -> ModRegistry.REMOTE_SUPPORT_MODULE.get();
            case SEISMIC_SHOCK -> ModRegistry.SEISMIC_SHOCK_MODULE.get();
            case ARMOR_BREAK -> ModRegistry.ARMOR_BREAK_MODULE.get();
            case CLUSTER_SHELLS -> ModRegistry.CLUSTER_SHELLS_MODULE.get();
        };
        return new ItemStack(moduleItem);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos installPos = resolveInstallPos(level, context.getClickedPos());
        BlockEntity blockEntity = level.getBlockEntity(installPos);
        return tryInstall(level, installPos, player, context.getHand(), blockEntity);
    }

    private static BlockPos resolveInstallPos(Level level, BlockPos clickedPos) {
        BlockState state = level.getBlockState(clickedPos);
        if (state.getBlock() instanceof GrandCannonBlock
                && state.hasProperty(GrandCannonBlock.PART)
                && state.hasProperty(GrandCannonBlock.FACING)) {
            GrandCannonBlock.CannonPart part = state.getValue(GrandCannonBlock.PART);
            Direction facing = state.getValue(GrandCannonBlock.FACING);
            return part == GrandCannonBlock.CannonPart.BACK_LEFT
                    ? clickedPos
                    : part.getCorePos(clickedPos, facing);
        }
        return clickedPos;
    }
}
