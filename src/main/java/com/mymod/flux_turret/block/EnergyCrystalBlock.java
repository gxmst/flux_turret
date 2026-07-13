package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import com.mymod.flux_turret.item.EnergyCrystalItem;
import com.mymod.flux_turret.util.ChargeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EnergyCrystalBlock extends BaseEntityBlock {
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    private final int energyMultiplier;

    public EnergyCrystalBlock(Properties properties) {
        this(properties, 1);
    }

    public EnergyCrystalBlock(Properties properties, int energyMultiplier) {
        super(properties);
        this.energyMultiplier = Math.max(1, energyMultiplier);
        this.registerDefaultState(this.stateDefinition.any().setValue(FULL, false));
    }

    public int getEnergyMultiplier() {
        return energyMultiplier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FULL);
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

            InteractionResult chargeResult = ChargeHelper.tryRedstoneCharge(
                    level, pos, state, player, heldItem, be.getEnergyStorage(),
                    TurretConfig.ENERGY_CRYSTAL_REDSTONE_CHARGE.get() * be.getEnergyMultiplier(),
                    TurretConfig.ENERGY_CRYSTAL_REDSTONE_BLOCK_CHARGE.get() * be.getEnergyMultiplier());
            if (chargeResult != null) {
                be.setChanged();
                return chargeResult;
            }

            // Otherwise, display current charge info
            int stored = be.getEnergyStorage().getEnergyStored();
            int max = be.getEnergyStorage().getMaxEnergyStored();
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.flux_turret.crystal_status", stored, max)
                        .withStyle(net.minecraft.ChatFormatting.AQUA),
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
                if (crystalBe.isEmpowered()) {
                    drops.add(EnergyCrystalItem.createChargedStack(ModRegistry.EMPOWERED_ENERGY_CRYSTAL_ITEM.get(), 0));
                } else {
                    // Completely empty normal crystal drops the Depleted Empty Crystal item.
                    drops.add(new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get()));
                }
            } else {
                // Charged crystal drops the charged crystal block item with energy NBT intact!
                drops.add(EnergyCrystalItem.createChargedStack(crystalBe.isEmpowered()
                        ? ModRegistry.EMPOWERED_ENERGY_CRYSTAL_ITEM.get()
                        : ModRegistry.ENERGY_CRYSTAL_ITEM.get(), energy));
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
            return ChargeHelper.energySignal(
                    be.getEnergyStorage().getEnergyStored(),
                    be.getEnergyStorage().getMaxEnergyStored());
        }
        return 0;
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof EnergyCrystalBlockEntity be) {
            int remaining = be.getEnergyStorage().getEnergyStored();
            if (remaining <= 0) {
                return be.isEmpowered()
                        ? EnergyCrystalItem.createChargedStack(ModRegistry.EMPOWERED_ENERGY_CRYSTAL_ITEM.get(), 0)
                        : new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get());
            }
            return EnergyCrystalItem.createChargedStack(be.isEmpowered()
                    ? ModRegistry.EMPOWERED_ENERGY_CRYSTAL_ITEM.get()
                    : ModRegistry.ENERGY_CRYSTAL_ITEM.get(), remaining);
        }
        return new ItemStack(ModRegistry.EMPTY_CRYSTAL_ITEM.get());
    }
}
