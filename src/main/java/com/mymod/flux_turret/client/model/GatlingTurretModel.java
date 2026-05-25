package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class GatlingTurretModel extends GeoModel<GatlingTurretBlockEntity> {
    @Override
    public ResourceLocation getModelResource(GatlingTurretBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "geo/block/gatling_turret.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GatlingTurretBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "textures/block/gatling_turret.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GatlingTurretBlockEntity animatable) {
        return new ResourceLocation(FluxTurretMod.MOD_ID, "animations/block/gatling_turret.animation.json");
    }

    @Override
    public void setCustomAnimations(GatlingTurretBlockEntity animatable, long instanceId,
            AnimationState<GatlingTurretBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone turret = getAnimationProcessor().getBone("turret");
        CoreGeoBone left = getAnimationProcessor().getBone("barrels_left");
        CoreGeoBone right = getAnimationProcessor().getBone("barrels_right");

        if (animatable.getLevel() == null)
            return;

        if (turret != null && animatable.visualTargetId != -1) {
            Entity target = animatable.getLevel().getEntity(animatable.visualTargetId);
            if (target != null) {
                double dx = target.getX() - (animatable.getBlockPos().getX() + 0.5);
                double dz = target.getZ() - (animatable.getBlockPos().getZ() + 0.5);
                turret.setRotY(-(float) Math.atan2(dx, -dz));
            }
        }

        float spin = animatable.getSpinUp() / 200.0f;
        float speed = spin <= 0.0f ? 0.0f : 0.08f + spin * spin * 2.8f;
        float rotation = (animatable.getLevel().getGameTime() % 360) * speed;
        if (left != null)
            left.setRotZ(rotation);
        if (right != null)
            right.setRotZ(-rotation);
    }
}
