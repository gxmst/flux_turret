package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class EnergyCrystalModel extends GeoModel<EnergyCrystalBlockEntity> {
    @Override
    public ResourceLocation getModelResource(EnergyCrystalBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "geo/block/energy_crystal.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EnergyCrystalBlockEntity animatable) {
        if (animatable.getEnergyStorage().getEnergyStored() <= 0) {
            return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "textures/block/empty_crystal.png");
        }
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "textures/block/energy_crystal.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EnergyCrystalBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "animations/block/energy_crystal.animation.json");
    }

    @Override
    public void setCustomAnimations(EnergyCrystalBlockEntity animatable, long instanceId,
            AnimationState<EnergyCrystalBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
    }
}
