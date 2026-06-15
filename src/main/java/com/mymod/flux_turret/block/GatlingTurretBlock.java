package com.mymod.flux_turret.block;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;

public class GatlingTurretBlock extends TurretBlockBase {
    public GatlingTurretBlock(Properties properties) {
        super(properties, ModRegistry.GATLING_TURRET_BE, 2, GatlingTurretBlockEntity::tick);
    }
}
