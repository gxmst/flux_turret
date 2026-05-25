package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;

public class PrismTowerModel extends GeoModel<PrismTowerBlockEntity> {
    @Override
    public ResourceLocation getModelResource(PrismTowerBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "geo/block/prism_tower.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PrismTowerBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "textures/block/prism_tower.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PrismTowerBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "animations/block/prism_tower.animation.json");
    }

    @Override
    public void setCustomAnimations(PrismTowerBlockEntity animatable, long instanceId,
            AnimationState<PrismTowerBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
    }
}
