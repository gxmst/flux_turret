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
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "geo/block/gatling_turret.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GatlingTurretBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "textures/block/gatling_turret.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GatlingTurretBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "animations/block/gatling_turret.animation.json");
    }

    @Override
    public void setCustomAnimations(GatlingTurretBlockEntity animatable, long instanceId,
            AnimationState<GatlingTurretBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone mount = getAnimationProcessor().getBone("mount");
        CoreGeoBone gun = getAnimationProcessor().getBone("gun");
        CoreGeoBone barrelsLeft = getAnimationProcessor().getBone("barrels_left");
        CoreGeoBone barrelsRight = getAnimationProcessor().getBone("barrels_right");

        if (animatable.getLevel() == null)
            return;

        if (mount != null && gun != null) {
            double tx = 0, ty = 0, tz = 0;
            boolean haveAim = false;

            // Prefer the live target entity for smooth per-frame tracking, but only
            // if this client actually has it loaded. In multiplayer a distant client
            // may not track the mob, so fall back to the server-synced aim point,
            // which keeps the barrel oriented correctly instead of frozen.
            if (animatable.visualTargetId != -1) {
                Entity target = animatable.getLevel().getEntity(animatable.visualTargetId);
                if (target != null) {
                    tx = target.getX();
                    ty = target.getEyeY();
                    tz = target.getZ();
                    haveAim = true;
                }
            }
            if (!haveAim && animatable.hasAimTarget()) {
                tx = animatable.getAimTargetX();
                ty = animatable.getAimTargetY();
                tz = animatable.getAimTargetZ();
                haveAim = true;
            }

            if (haveAim) {
                double dx = tx - (animatable.getBlockPos().getX() + 0.5);
                double dy = ty - (animatable.getBlockPos().getY() + 1.5);
                double dz = tz - (animatable.getBlockPos().getZ() + 0.5);
                double dist = Math.sqrt(dx * dx + dz * dz);
                mount.setRotY(-(float) Math.atan2(dx, -dz));
                gun.setRotX((float) Math.atan2(dy, dist));
            }
        }

        float spin = animatable.getSpinUp() / 200.0f;
        if (spin > 0) {
            float speed = 0.08f + spin * spin * 2.8f;
            float rotation = (animatable.getLevel().getGameTime() % 360) * speed;
            if (barrelsLeft != null) barrelsLeft.setRotZ(rotation);
            if (barrelsRight != null) barrelsRight.setRotZ(-rotation);
        }
    }
}
