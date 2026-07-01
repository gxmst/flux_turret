package com.mymod.flux_turret.item;

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
