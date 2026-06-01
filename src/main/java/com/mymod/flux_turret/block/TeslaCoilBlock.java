package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TeslaCoilBlock extends TurretBlockBase {
    public TeslaCoilBlock(Properties properties) {
        super(properties, ModRegistry.TESLA_COIL_BE, 3);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.TESLA_COIL_BE.get(), TeslaCoilBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof TeslaCoilBlockEntity tesla) {
            if (hand == InteractionHand.MAIN_HAND) {
                if (player.getFoodData().getFoodLevel() > 6 || player.isCreative()) {
                    if (!level.isClientSide) {
                        tesla.performManualCrank();

                        if (!player.isCreative()) {
                            player.causeFoodExhaustion(1.5f);
                        }

                        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.7f, 1.5f);
                        level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.5f, 1.8f);
                    } else {
                        double px = pos.getX() + 0.5;
                        double py = pos.getY() + 2.5;
                        double pz = pos.getZ() + 0.5;
                        for (int i = 0; i < 5; i++) {
                            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                                    px + (level.random.nextDouble() - 0.5) * 0.6,
                                    py + level.random.nextDouble() * 1.5,
                                    pz + (level.random.nextDouble() - 0.5) * 0.6,
                                    0, 0.05, 0);
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else {
                    if (level.isClientSide) {
                        player.displayClientMessage(
                                Component.literal("\u00A7c\u809A\u5B50\u592A\u997F\u4E86\uFF0C\u6447\u4E0D\u52A8\u624B\u6447\u7279\u65AF\u62C9\u7EBF\u5708\uFF01\u5148\u5403\u70B9\u4E1C\u897F\u5427\uFF01"),
                                true);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return super.use(state, level, pos, player, hand, hit);
    }
}
