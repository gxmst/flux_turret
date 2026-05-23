package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import com.mymod.flux_turret.client.model.GatlingTurretModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class GatlingTurretRenderer implements BlockEntityRenderer<GatlingTurretBlockEntity> {
    private final GeoBlockRenderer<GatlingTurretBlockEntity> geckoRenderer;

    public GatlingTurretRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new GatlingTurretModel());
    }

    @Override
    public void render(GatlingTurretBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (be.getLevel() == null)
            return;

        long timeDiff = be.getLevel().getGameTime() - be.getLastFireTime();
        boolean isFiringWindow = timeDiff >= 0 && timeDiff <= 2;

        if (be.visualTargetId != -1) {
            Entity target = be.getLevel().getEntity(be.visualTargetId);
            if (target != null && target.isAlive())
                be.visualCachedTargetPos = target.getEyePosition(partialTick);
        }

        if (isFiringWindow && be.visualCachedTargetPos != null) {
            renderTracer(be, be.visualCachedTargetPos, poseStack, bufferSource);
        } else if (!isFiringWindow) {
            be.visualCachedTargetPos = null;
        }
    }

    private void renderTracer(GatlingTurretBlockEntity be, Vec3 targetWorldPos, PoseStack poseStack,
            MultiBufferSource bufferSource) {
        Vec3 bePos = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 end = targetWorldPos.subtract(bePos);
        Vec3 center = new Vec3(0.5, 1.05, 0.5);
        Vec3 aim = new Vec3(end.x - center.x, 0, end.z - center.z);
        if (aim.lengthSqr() < 0.0001)
            aim = new Vec3(0, 0, -1);
        Vec3 forward = aim.normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize().scale(0.32);
        Vec3 leftMuzzle = center.add(forward.scale(0.75)).add(side);
        Vec3 rightMuzzle = center.add(forward.scale(0.75)).subtract(side);
        Vec3 direction = end.subtract(center);
        if (direction.lengthSqr() < 0.0001)
            return;

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        drawMuzzleTracer(matrix, buffer, leftMuzzle, end);
        drawMuzzleTracer(matrix, buffer, rightMuzzle, end);
    }

    private void drawMuzzleTracer(Matrix4f matrix, VertexConsumer buffer, Vec3 muzzle, Vec3 target) {
        Vec3 direction = target.subtract(muzzle);
        if (direction.lengthSqr() < 0.0001)
            return;
        Vec3 normal = direction.normalize();
        Vec3 tracerEnd = muzzle.add(normal.scale(Math.min(3.5, direction.length())));
        RenderUtils.drawBeam(matrix, buffer, muzzle, tracerEnd, 0.022f, 255, 185, 50, 225);
        RenderUtils.drawBeam(matrix, buffer, muzzle, muzzle.add(normal.scale(0.42)), 0.06f, 255, 95, 20, 210);
    }
}
