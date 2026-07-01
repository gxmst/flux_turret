package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.client.model.TeslaCoilModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class TeslaCoilRenderer implements BlockEntityRenderer<TeslaCoilBlockEntity> {
    private static final double IDLE_CURRENT_RENDER_DISTANCE_SQR = 36.0D * 36.0D;

    private final GeoBlockRenderer<TeslaCoilBlockEntity> geckoRenderer;

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new TeslaCoilModel());
        this.geckoRenderer.addRenderLayer(new AutoGlowingGeoLayer<>(this.geckoRenderer));
    }

    @Override
    public void render(TeslaCoilBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (be.getLevel() == null)
            return;

        if (be.isVisuallyPowered() && shouldRenderIdleCurrent(be))
            renderIdleCurrent(be, partialTick, poseStack, bufferSource);

        long timeDiff = be.getLevel().getGameTime() - be.getLastFireTime();
        boolean isFiringWindow = timeDiff >= 0 && timeDiff <= 6;

        if (be.visualTargetId != -1) {
            Entity target = be.getLevel().getEntity(be.visualTargetId);
            if (target != null && target.isAlive())
                be.visualCachedTargetPos = target.getEyePosition(partialTick);
        }

        if (isFiringWindow && be.visualCachedTargetPos != null) {
            renderArc(be, be.visualCachedTargetPos, poseStack, bufferSource);
        } else if (!isFiringWindow) {
            be.visualCachedTargetPos = null;
        }
    }

    private boolean shouldRenderIdleCurrent(TeslaCoilBlockEntity be) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(cameraPos) <= IDLE_CURRENT_RENDER_DISTANCE_SQR;
    }

    private void renderIdleCurrent(TeslaCoilBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        double time = (be.getLevel().getGameTime() + partialTick) * 0.12;
        for (int strand = 0; strand < 3; strand++) {
            double phase = time + strand * Math.PI * 2.0 / 3.0;
            Vec3 previous = null;
            int segments = 24;

            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double angle = phase + t * Math.PI * 4.0;
                double radius = 0.34 + 0.08 * Math.sin(time * 2.1 + t * Math.PI * 2.0 + strand);
                double y = 1.05 + t * 1.95;
                Vec3 point = new Vec3(
                        0.5 + Math.cos(angle) * radius,
                        y,
                        0.5 + Math.sin(angle) * radius);

                if (previous != null) {
                    float width = strand == 0 ? 0.017f : 0.012f;
                    int alpha = strand == 0 ? 118 : 82;
                    RenderUtils.drawBeam(matrix, buffer, previous, point, width, 118, 180, 255, alpha);
                    if (strand == 0 && i % 6 == 0) {
                        RenderUtils.drawBeam(matrix, buffer, previous, point, width * 0.45f, 245, 250, 255, 160);
                    }
                }
                previous = point;
            }
        }
    }

    private void renderArc(TeslaCoilBlockEntity be, Vec3 targetWorldPos, PoseStack poseStack,
            MultiBufferSource bufferSource) {
        Vec3 start = new Vec3(0.5, 2.875, 0.5);
        Vec3 bePos = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 end = targetWorldPos.subtract(bePos);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        long seed = be.getLevel().getGameTime() + be.getBlockPos().asLong();
        Vec3 previous = start;
        int segments = 7;

        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            Vec3 point = start.lerp(end, t);
            if (i < segments) {
                double offsetA = Math.sin(seed * 0.7 + i * 2.1) * 0.22;
                double offsetB = Math.cos(seed * 0.55 + i * 1.7) * 0.22;
                point = point.add(offsetA, Math.sin(seed + i) * 0.10, offsetB);
            }

            float width = i % 2 == 0 ? 0.07f : 0.045f;
            RenderUtils.drawBeam(matrix, buffer, previous, point, width, 115, 160, 255, 240);
            RenderUtils.drawBeam(matrix, buffer, previous, point, width * 0.35f, 245, 245, 255, 255);
            previous = point;
        }
    }
}
