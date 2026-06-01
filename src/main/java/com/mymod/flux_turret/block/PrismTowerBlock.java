package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PrismTowerBlock extends TurretBlockBase {
    public PrismTowerBlock(Properties properties) {
        super(properties, ModRegistry.PRISM_TOWER_BE, 3);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.PRISM_TOWER_BE.get(), PrismTowerBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof PrismTowerBlockEntity prism) {
            if (stack.getItem() instanceof DyeItem dyeItem) {
                if (!level.isClientSide) {
                    DyeColor color = dyeItem.getDyeColor();
                    prism.setDyeColorIndex(color.getId());

                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }

                    prism.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);

                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                } else {
                    double px = pos.getX() + 0.5;
                    double py = pos.getY() + 3.125;
                    double pz = pos.getZ() + 0.5;
                    for (int i = 0; i < 8; i++) {
                        level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                                px + (level.random.nextDouble() - 0.5) * 0.4,
                                py + (level.random.nextDouble() - 0.5) * 0.4,
                                pz + (level.random.nextDouble() - 0.5) * 0.4,
                                0, 0.1, 0);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (stack.is(Items.WATER_BUCKET)
                    || (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER)) {
                if (!level.isClientSide) {
                    prism.setDyeColorIndex(-1);
                    prism.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.2f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.use(state, level, pos, player, hand, hit);
    }
}
