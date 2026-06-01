package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PsychicBeaconModel extends GeoModel<PsychicBeaconBlockEntity> {
    @Override
    public ResourceLocation getModelResource(PsychicBeaconBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "geo/block/psychic_beacon.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PsychicBeaconBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "textures/block/psychic_beacon.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PsychicBeaconBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "animations/block/psychic_beacon.animation.json");
    }
}
