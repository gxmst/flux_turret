package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.item.TurretUpgradeModuleItem;
import com.mymod.flux_turret.network.ConfigureTurretPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TeslaCoilBlock extends TurretBlockBase {
    public TeslaCoilBlock(Properties properties) {
        super(properties, ModRegistry.TESLA_COIL_BE, 3, TeslaCoilBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof TeslaCoilBlockEntity tesla) {
            InteractionResult upgradeResult = TurretUpgradeModuleItem.tryHandleInteraction(level, pos, player, hand, be);
            if (upgradeResult != InteractionResult.PASS) {
                return upgradeResult;
            }
            if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                    && player.getItemInHand(hand).isEmpty()) {
                if (!level.isClientSide) {
                    tesla.claimIfUnowned(player);
                    if (!tesla.canPlayerConfigure(player)) {
                        player.displayClientMessage(Component.translatable(
                                "message.flux_turret.turret_access_denied"), true);
                        return InteractionResult.CONSUME;
                    }
                }
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    ConfigureTurretPacket.showManualCrankFailure(
                            serverPlayer, tesla.tryManualCrank(serverPlayer));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.use(state, level, pos, player, hand, hit);
    }
}
