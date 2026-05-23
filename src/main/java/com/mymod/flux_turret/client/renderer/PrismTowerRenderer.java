package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.client.model.PrismTowerModel;
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
            float pulse = (float) Math.sin(gameTime * 0.5) * 0.01f;
            startWidth = 0.05f + pulse;
            endWidth = 0.05f + pulse;
            r = 100;
            g = 255;
            b = 255;
            alpha = 210;
        } else {
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

        RenderUtils.drawBeam(matrix, buffer, start, end, startWidth, endWidth, r, g, b, alpha);

        if (!isSupport) {
            RenderUtils.drawBeam(matrix, buffer, start, end, startWidth * 0.2f, endWidth * 0.2f, 255, 255, 255, 255);
        }
    }
}
