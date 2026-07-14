package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.item.TurretUpgradeModuleItem;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;

public class PrismTowerBlock extends TurretBlockBase {
    public PrismTowerBlock(Properties properties) {
        super(properties, ModRegistry.PRISM_TOWER_BE, 3, PrismTowerBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof PrismTowerBlockEntity prism) {
            InteractionResult upgradeResult = TurretUpgradeModuleItem.tryHandleInteraction(level, pos, player, hand, be);
            if (upgradeResult != InteractionResult.PASS) {
                return upgradeResult;
            }

            if (stack.getItem() instanceof DyeItem dyeItem) {
                if (level.isClientSide) return InteractionResult.SUCCESS;

                DyeColor color = dyeItem.getDyeColor();
                if (prism.getDyeColorIndex() == color.getId()) return InteractionResult.CONSUME;
                if (!claimAndCheckAccess(prism, player)) return InteractionResult.CONSUME;

                prism.setDyeColorIndex(color.getId());
                if (!player.isCreative()) stack.shrink(1);
                finishColorChange((ServerLevel) level, pos, state, prism, true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (stack.is(Items.WATER_BUCKET)
                    || (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER)) {
                if (level.isClientSide) return InteractionResult.SUCCESS;
                if (prism.getDyeColorIndex() == -1) return InteractionResult.CONSUME;
                if (!claimAndCheckAccess(prism, player)) return InteractionResult.CONSUME;

                prism.setDyeColorIndex(-1);
                if (!player.isCreative()) {
                    player.setItemInHand(hand, new ItemStack(
                            stack.is(Items.WATER_BUCKET) ? Items.BUCKET : Items.GLASS_BOTTLE));
                }
                finishColorChange((ServerLevel) level, pos, state, prism, false);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.use(state, level, pos, player, hand, hit);
    }

    private static boolean claimAndCheckAccess(PrismTowerBlockEntity prism, Player player) {
        if (player instanceof FakePlayer) return false;
        prism.claimIfUnowned(player);
        if (prism.canPlayerConfigure(player)) return true;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.flux_turret.turret_access_denied"), true);
        return false;
    }

    private static void finishColorChange(ServerLevel level, BlockPos pos, BlockState state,
                                          PrismTowerBlockEntity prism, boolean dyed) {
        prism.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        level.playSound(null, pos, dyed ? SoundEvents.DYE_USE : SoundEvents.BUCKET_EMPTY,
                SoundSource.BLOCKS, 1.0f, dyed ? 1.0f : 1.2f);
        if (dyed) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 3.125, pos.getZ() + 0.5,
                    8, 0.2, 0.2, 0.2, 0.1);
        }
    }
}
