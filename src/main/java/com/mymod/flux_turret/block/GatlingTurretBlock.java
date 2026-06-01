package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class GatlingTurretBlock extends TurretBlockBase {
    public GatlingTurretBlock(Properties properties) {
        super(properties, ModRegistry.GATLING_TURRET_BE, 2);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, ModRegistry.GATLING_TURRET_BE.get(), GatlingTurretBlockEntity::tick);
    }
}
