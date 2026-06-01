package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.client.model.PsychicBeaconModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PsychicBeaconRenderer implements BlockEntityRenderer<PsychicBeaconBlockEntity> {
    private final GeoBlockRenderer<PsychicBeaconBlockEntity> geckoRenderer;

    public PsychicBeaconRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new PsychicBeaconModel());
        this.geckoRenderer.addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this.geckoRenderer));
    }

    @Override
    public void render(PsychicBeaconBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        int state = be.getBeaconState();
        if (state == 0 || state == 3) return;

        float[] color = getBeamColor(state, be.getLevel().getGameTime(), partialTick);
        renderBeam(poseStack, bufferSource, color, partialTick, be.getLevel().getGameTime());
    }

    private float[] getBeamColor(int state, long gameTime, float partialTick) {
        return switch (state) {
            case 1 -> new float[]{0.87f, 0.27f, 0.73f};
            case 2 -> new float[]{0.80f, 0.13f, 0.27f};
            case 4 -> {
                float pulse = 0.5f + 0.5f * Mth.sin((gameTime + partialTick) * 0.4f);
                yield new float[]{0.9f + 0.1f * pulse, 0.1f * pulse, 0.1f * pulse};
            }
            default -> new float[]{0.87f, 0.27f, 0.73f};
        };
    }

    private void renderBeam(PoseStack poseStack, MultiBufferSource bufferSource, float[] color, float partialTick, long gameTime) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);

        float time = (float)gameTime + partialTick;
        float rotation = time * 0.05f;

        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        float beamRadius = 0.15f;
        float beamHeight = 300.0f;
        float innerRadius = beamRadius * 0.6f;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lightning());

        Matrix4f matrix = poseStack.last().pose();
        float r = color[0], g = color[1], b = color[2];

        int alpha = 153;
        int innerAlpha = 89;

        consumer.vertex(matrix, -beamRadius, 2.0f, -beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, beamRadius, 2.0f, -beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, beamRadius, 2.0f, beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, -beamRadius, 2.0f, beamRadius).color(r, g, b, alpha / 255f).endVertex();

        consumer.vertex(matrix, -beamRadius, beamHeight, -beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, beamRadius, beamHeight, -beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, beamRadius, beamHeight, beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, -beamRadius, beamHeight, beamRadius).color(r, g, b, 0.0f).endVertex();

        consumer.vertex(matrix, -beamRadius, 2.0f, -beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, -beamRadius, beamHeight, -beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, beamRadius, beamHeight, -beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, beamRadius, 2.0f, -beamRadius).color(r, g, b, alpha / 255f).endVertex();

        consumer.vertex(matrix, beamRadius, 2.0f, beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, beamRadius, beamHeight, beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, -beamRadius, beamHeight, beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, -beamRadius, 2.0f, beamRadius).color(r, g, b, alpha / 255f).endVertex();

        consumer.vertex(matrix, -beamRadius, 2.0f, beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, -beamRadius, beamHeight, beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, -beamRadius, beamHeight, -beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, -beamRadius, 2.0f, -beamRadius).color(r, g, b, alpha / 255f).endVertex();

        consumer.vertex(matrix, beamRadius, 2.0f, -beamRadius).color(r, g, b, alpha / 255f).endVertex();
        consumer.vertex(matrix, beamRadius, beamHeight, -beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, beamRadius, beamHeight, beamRadius).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, beamRadius, 2.0f, beamRadius).color(r, g, b, alpha / 255f).endVertex();

        float ir = Math.min(r + 0.15f, 1.0f);
        float ig = Math.min(g + 0.15f, 1.0f);
        float ib = Math.min(b + 0.15f, 1.0f);

        consumer.vertex(matrix, -innerRadius, 2.0f, -innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, innerRadius, 2.0f, -innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, innerRadius, 2.0f, innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, -innerRadius, 2.0f, innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();

        consumer.vertex(matrix, -innerRadius, beamHeight, -innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, innerRadius, beamHeight, -innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, innerRadius, beamHeight, innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, -innerRadius, beamHeight, innerRadius).color(ir, ig, ib, 0.0f).endVertex();

        consumer.vertex(matrix, -innerRadius, 2.0f, -innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, -innerRadius, beamHeight, -innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, innerRadius, beamHeight, -innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, innerRadius, 2.0f, -innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();

        consumer.vertex(matrix, innerRadius, 2.0f, innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, innerRadius, beamHeight, innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, -innerRadius, beamHeight, innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, -innerRadius, 2.0f, innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();

        consumer.vertex(matrix, -innerRadius, 2.0f, innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, -innerRadius, beamHeight, innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, -innerRadius, beamHeight, -innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, -innerRadius, 2.0f, -innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();

        consumer.vertex(matrix, innerRadius, 2.0f, -innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();
        consumer.vertex(matrix, innerRadius, beamHeight, -innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, innerRadius, beamHeight, innerRadius).color(ir, ig, ib, 0.0f).endVertex();
        consumer.vertex(matrix, innerRadius, 2.0f, innerRadius).color(ir, ig, ib, innerAlpha / 255f).endVertex();

        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
