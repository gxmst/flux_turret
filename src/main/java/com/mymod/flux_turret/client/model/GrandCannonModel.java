package com.mymod.flux_turret.client.model;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class GrandCannonModel extends GeoModel<GrandCannonBlockEntity> {
    @Override
    public ResourceLocation getModelResource(GrandCannonBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "geo/block/grand_cannon.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GrandCannonBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "textures/block/grand_cannon.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GrandCannonBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "animations/block/grand_cannon.animation.json");
    }

    @Override
    public void setCustomAnimations(GrandCannonBlockEntity animatable, long instanceId, AnimationState<GrandCannonBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone mount = getAnimationProcessor().getBone("mount");
        CoreGeoBone gun = getAnimationProcessor().getBone("gun");

        if (animatable.getLevel() == null)
            return;

        // Only the core block entity handles aiming
        if (animatable.getBlockState().getValue(GrandCannonBlock.PART) != GrandCannonBlock.CannonPart.BACK_LEFT)
            return;

        if (mount != null && gun != null && animatable.visualTargetId != -1) {
            Entity target = animatable.getLevel().getEntity(animatable.visualTargetId);
            if (target != null) {
                // Calculate center of 2x2 structure relative to core block
                Direction facing = animatable.getBlockState().getValue(GrandCannonBlock.FACING);
                Direction right = facing.getClockWise();
                double centerX = animatable.getBlockPos().getX() + 0.5 + facing.getStepX() * 0.5 + right.getStepX() * 0.5;
                double centerZ = animatable.getBlockPos().getZ() + 0.5 + facing.getStepZ() * 0.5 + right.getStepZ() * 0.5;
                double centerY = animatable.getBlockPos().getY() + 1.5;

                double dx = target.getX() - centerX;
                double dy = target.getEyeY() - centerY;
                double dz = target.getZ() - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                mount.setRotY(-(float) Math.atan2(dx, -dz));
                gun.setRotX((float) Math.atan2(dy, dist));
            }
        }
    }
}
