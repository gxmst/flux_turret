package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.client.model.PrismTowerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PrismTowerRenderer implements BlockEntityRenderer<PrismTowerBlockEntity> {
    private final GeoBlockRenderer<PrismTowerBlockEntity> geckoRenderer;

    public PrismTowerRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new PrismTowerModel());
    }

    @Override
    public void render(PrismTowerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        if (be.getLevel() == null)
            return;

        long timeDiff = be.getLevel().getGameTime() - be.getLastFireTime();
        boolean isFiringWindow = timeDiff >= 0 && timeDiff <= 5;

        // Determine target position and laser type
        boolean isSupport = false;

        if (be.visualTargetType == 1 && be.visualTargetId != -1) {
            Entity target = be.getLevel().getEntity(be.visualTargetId);
            if (target != null && target.isAlive()) {
                be.visualCachedTargetPos = target.getEyePosition(partialTick);
            }
            isSupport = false;
        } else if (be.visualTargetType == 2 && be.visualTargetPos != null) {
            be.visualCachedTargetPos = Vec3.atLowerCornerOf(be.visualTargetPos).add(0.5, 2.875, 0.5);
            isSupport = true;
        }

        if (isFiringWindow && be.visualCachedTargetPos != null) {
            renderLaser(be, be.visualCachedTargetPos, isSupport, be.getSupportCount(), poseStack, bufferSource);
        } else if (!isFiringWindow) {
            be.visualCachedTargetPos = null;
        }
    }

    private void renderLaser(PrismTowerBlockEntity be, Vec3 targetWorldPos, boolean isSupport, int supportCount,
            PoseStack poseStack, MultiBufferSource bufferSource) {
        Vec3 start = new Vec3(0.5, 2.875, 0.5);
        Vec3 bePos = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 end = targetWorldPos.subtract(bePos);

        long gameTime = be.getLevel().getGameTime();
        float startWidth, endWidth;
        int r, g, b, alpha;

        if (isSupport) {
            // Support Laser: Thin, semi-transparent, slight pulse
            float pulse = (float) Math.sin(gameTime * 0.5) * 0.01f;
            startWidth = 0.05f + pulse;
            endWidth = 0.05f + pulse;
            r = 100;
            g = 255;
            b = 255;
            alpha = 210; // Increased Alpha (0.6 -> 0.8)
        } else {
            // Attack Laser: Thick, opaque, strong pulse
            float pulse = (float) Math.sin(gameTime * 0.5) * 0.025f;
            float supportScale = (float) Math.min(1.0d, Math.log1p(Math.max(0, supportCount)) / Math.log(8.0d));
            float baseWidth = 0.16f + supportScale * 0.34f;
            startWidth = baseWidth * 0.45f + pulse;
            endWidth = baseWidth + pulse;
            r = 0;
            g = 255;
            b = 255;
            alpha = 255;
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        // Render Main Beam
        drawBeam(matrix, buffer, start, end, startWidth, endWidth, r, g, b, alpha);

        // Render White Core for Attack Beam
        if (!isSupport) {
            drawBeam(matrix, buffer, start, end, startWidth * 0.2f, endWidth * 0.2f, 255, 255, 255, 255);
        }
    }

    private void drawBeam(Matrix4f matrix, VertexConsumer buffer, Vec3 start, Vec3 end, float startWidth,
            float endWidth, int r, int g, int b, int a) {
        Vec3 diff = end.subtract(start);
        Vec3 normal = diff.normalize();
        Vec3 v1 = normal.cross(new Vec3(0, 1, 0));
        if (v1.lengthSqr() < 0.01) {
            v1 = normal.cross(new Vec3(1, 0, 0));
        }
        v1 = v1.normalize();
        Vec3 v2 = normal.cross(v1).normalize();

        drawQuad(matrix, buffer, start, end, v1, startWidth, endWidth, r, g, b, a);
        drawQuad(matrix, buffer, start, end, v2, startWidth, endWidth, r, g, b, a);
    }

    private void drawQuad(Matrix4f matrix, VertexConsumer buffer, Vec3 start, Vec3 end, Vec3 offsetDir,
            float startWidth, float endWidth, int r, int g, int b, int a) {
        Vec3 s1 = start.add(offsetDir.scale(startWidth));
        Vec3 s2 = start.subtract(offsetDir.scale(startWidth));
        Vec3 e1 = end.add(offsetDir.scale(endWidth));
        Vec3 e2 = end.subtract(offsetDir.scale(endWidth));

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
