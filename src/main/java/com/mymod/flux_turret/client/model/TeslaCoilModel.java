package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class TeslaCoilModel extends GeoModel<TeslaCoilBlockEntity> {
    @Override
    public ResourceLocation getModelResource(TeslaCoilBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "geo/block/tesla_coil.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TeslaCoilBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "textures/block/tesla_coil.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TeslaCoilBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "animations/block/tesla_coil.animation.json");
    }

    @Override
    public void setCustomAnimations(TeslaCoilBlockEntity animatable, long instanceId,
            AnimationState<TeslaCoilBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone topCoil = getAnimationProcessor().getBone("top_coil");

        if (topCoil != null && animatable.getLevel() != null && animatable.isVisuallyPowered()) {
            float rotation = (animatable.getLevel().getGameTime() % 360) * 0.12f;
            topCoil.setRotY(rotation);
        }
    }
}
