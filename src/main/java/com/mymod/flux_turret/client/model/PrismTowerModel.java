package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

public class PrismTowerModel extends GeoModel<PrismTowerBlockEntity> {
    @Override
    public ResourceLocation getModelResource(PrismTowerBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "geo/block/prism_tower.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PrismTowerBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "textures/block/prism_tower.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PrismTowerBlockEntity animatable) {
        return null; // No animation file yet, handled by code
    }

    @Override
    public void setCustomAnimations(PrismTowerBlockEntity animatable, long instanceId,
            AnimationState<PrismTowerBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone turret = getAnimationProcessor().getBone("turret");

        // Only rotate when having energy (synced boolean from server)
        if (turret != null && animatable.visualHasEnergy) {
            float rotation = (animatable.getLevel().getGameTime() % 360) * (float) (Math.PI / 180f);
            turret.setRotY(rotation);
        }
    }
}
