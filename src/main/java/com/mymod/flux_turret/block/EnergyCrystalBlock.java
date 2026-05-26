package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EnergyCrystalBlock extends BaseEntityBlock {
    public EnergyCrystalBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyCrystalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.ENERGY_CRYSTAL_BE.get(), EnergyCrystalBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            ItemStack heldItem = player.getItemInHand(hand);
            
            // Allow manual redstone dust charging
            if (heldItem.is(Items.REDSTONE)) {
                int capacity = be.getEnergyStorage().getMaxEnergyStored();
                int current = be.getEnergyStorage().getEnergyStored();
                if (current < capacity) {
                    int received = be.getEnergyStorage().receiveEnergy(TurretConfig.ENERGY_CRYSTAL_REDSTONE_CHARGE.get(), false);
                    if (received > 0) {
                        if (!player.getAbilities().instabuild) {
                            heldItem.shrink(1);
                        }
                        be.setChanged();
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.5f);
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(String.format("Charged: +%d FE (Energy: %d / %d FE)", received, be.getEnergyStorage().getEnergyStored(), capacity)),
                                true);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            
            // Allow manual redstone block charging
            if (heldItem.is(Items.REDSTONE_BLOCK)) {
                int capacity = be.getEnergyStorage().getMaxEnergyStored();
                int current = be.getEnergyStorage().getEnergyStored();
                if (current < capacity) {
                    int received = be.getEnergyStorage().receiveEnergy(TurretConfig.ENERGY_CRYSTAL_REDSTONE_BLOCK_CHARGE.get(), false);
                    if (received > 0) {
                        if (!player.getAbilities().instabuild) {
                            heldItem.shrink(1);
                        }
                        be.setChanged();
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.8f);
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(String.format("Charged: +%d FE (Energy: %d / %d FE)", received, be.getEnergyStorage().getEnergyStored(), capacity)),
                                true);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            
            // Otherwise, display current charge info
            int stored = be.getEnergyStorage().getEnergyStored();
            int max = be.getEnergyStorage().getMaxEnergyStored();
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(String.format("Energy: %d / %d FE", stored, max)),
                    true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        List<ItemStack> drops = new ArrayList<>();
        if (be instanceof EnergyCrystalBlockEntity crystalBe) {
            int energy = crystalBe.getEnergyStorage().getEnergyStored();
            if (energy <= 0) {
                // Completely empty crystal drops the Depleted Empty Crystal item!
                drops.add(new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get()));
            } else {
                // Charged crystal drops the charged crystal block item with energy NBT intact!
                ItemStack stack = new ItemStack(ModRegistry.ENERGY_CRYSTAL_ITEM.get());
                CompoundTag blockEntityTag = new CompoundTag();
                CompoundTag energyTag = new CompoundTag();
                energyTag.putInt("energy", energy);
                blockEntityTag.put("Energy", energyTag);
                stack.addTagElement("BlockEntityTag", blockEntityTag);
                drops.add(stack);
            }
        } else {
            drops.add(new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get()));
        }
        return drops;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            int stored = be.getEnergyStorage().getEnergyStored();
            int max = be.getEnergyStorage().getMaxEnergyStored();
            return (int) ((double) stored / max * 15);
        }
        return 0;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            int remaining = be.getEnergyStorage().getEnergyStored();
            if (remaining <= 0) {
                return new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get());
            }
            ItemStack stack = new ItemStack(ModRegistry.ENERGY_CRYSTAL_ITEM.get());
            CompoundTag blockEntityTag = new CompoundTag();
            CompoundTag energyTag = new CompoundTag();
            energyTag.putInt("energy", remaining);
            blockEntityTag.put("Energy", energyTag);
            stack.addTagElement("BlockEntityTag", blockEntityTag);
            return stack;
        }
        return new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get());
    }
}
