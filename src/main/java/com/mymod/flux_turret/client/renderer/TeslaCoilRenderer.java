package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.client.model.TeslaCoilModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TeslaCoilRenderer implements BlockEntityRenderer<TeslaCoilBlockEntity> {
    private final GeoBlockRenderer<TeslaCoilBlockEntity> geckoRenderer;

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new TeslaCoilModel());
    }

    @Override
    public void render(TeslaCoilBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (be.getLevel() == null)
            return;

        if (be.isVisuallyPowered())
            renderIdleCurrent(be, poseStack, bufferSource);

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

    private void renderIdleCurrent(TeslaCoilBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        double time = be.getLevel().getGameTime() * 0.22;
        for (int strand = 0; strand < 2; strand++) {
            double phase = time + strand * Math.PI;
            Vec3 previous = null;
            int segments = 12;

            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double angle = phase + t * Math.PI * 2.0;
                double radius = 0.42 + 0.05 * Math.sin(time * 1.7 + i);
                double y = 1.2 + t * 1.65;
                Vec3 point = new Vec3(
                        0.5 + Math.cos(angle) * radius,
                        y,
                        0.5 + Math.sin(angle) * radius);

                if (previous != null) {
                    int alpha = strand == 0 ? 150 : 105;
                    drawBeam(matrix, buffer, previous, point, strand == 0 ? 0.025f : 0.018f,
                            120, 175, 255, alpha);
                }
                previous = point;
            }
        }
    }

    private void renderArc(TeslaCoilBlockEntity be, Vec3 targetWorldPos, PoseStack poseStack,
            MultiBufferSource bufferSource) {
        Vec3 start = new Vec3(0.5, 3.5, 0.5);
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
            drawBeam(matrix, buffer, previous, point, width, 115, 160, 255, 240);
            drawBeam(matrix, buffer, previous, point, width * 0.35f, 245, 245, 255, 255);
            previous = point;
        }
    }

    private void drawBeam(Matrix4f matrix, VertexConsumer buffer, Vec3 start, Vec3 end, float width,
            int r, int g, int b, int a) {
        Vec3 diff = end.subtract(start);
        if (diff.lengthSqr() < 0.0001)
            return;

        Vec3 normal = diff.normalize();
        Vec3 side = normal.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.01)
            side = normal.cross(new Vec3(1, 0, 0));
        side = side.normalize().scale(width);

        Vec3 s1 = start.add(side);
        Vec3 s2 = start.subtract(side);
        Vec3 e1 = end.add(side);
        Vec3 e2 = end.subtract(side);

        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s2.x, (float) s2.y, (float) s2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e2.x, (float) e2.y, (float) e2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e2.x, (float) e2.y, (float) e2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s2.x, (float) s2.y, (float) s2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a).endVertex();
    }
}
