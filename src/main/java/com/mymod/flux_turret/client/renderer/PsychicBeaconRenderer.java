package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.client.model.PsychicBeaconModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PsychicBeaconRenderer implements BlockEntityRenderer<PsychicBeaconBlockEntity> {
    private static final int BASE_BEAM_HEIGHT = 96;
    private static boolean renderNetworkLinks = true;

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
        if (be.getLevel() == null || state == PsychicBeaconBlockEntity.STATE_OFFLINE
                || state == PsychicBeaconBlockEntity.STATE_FAILED) {
            return;
        }

        renderPsychicBeam(be, state, partialTick, poseStack, bufferSource);
        if (renderNetworkLinks) {
            renderNetworkLinks(be, state, partialTick, poseStack, bufferSource);
        }
    }

    public static boolean shouldRenderNetworkLinks() {
        return renderNetworkLinks;
    }

    public static void toggleNetworkLinks() {
        renderNetworkLinks = !renderNetworkLinks;
    }

    private void renderPsychicBeam(PsychicBeaconBlockEntity be, int state, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource) {
        int threatLevel = Mth.clamp(be.getThreatLevel(), 0, 4);
        long gameTime = be.getLevel().getGameTime();
        float pulse = 0.72f + 0.28f * Mth.sin((gameTime + partialTick) * (state == PsychicBeaconBlockEntity.STATE_WARNING ? 0.38f : 0.11f));
        float width = getBeamWidth(state, threatLevel) * pulse;
        int height = BASE_BEAM_HEIGHT + threatLevel * 28;
        int[] color = getBeamColor(state, threatLevel, gameTime, partialTick);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        Vec3 start = new Vec3(0.5, 1.72, 0.5);
        Vec3 end = new Vec3(0.5, 1.72 + height, 0.5);

        RenderUtils.drawBeam(matrix, buffer, start, end, width * 1.65f, color[0], color[1], color[2], 72);
        RenderUtils.drawBeam(matrix, buffer, start, end, width, color[0], color[1], color[2], 150);
        RenderUtils.drawBeam(matrix, buffer, start, end, Math.max(0.018f, width * 0.28f), 255, 230, 255, 205);

        double spin = (gameTime + partialTick) * 0.08;
        for (int i = 0; i < 3; i++) {
            double angle = spin + i * Math.PI * 2.0 / 3.0;
            double radius = 0.22 + threatLevel * 0.035;
            Vec3 lower = new Vec3(0.5 + Math.cos(angle) * radius, 1.9, 0.5 + Math.sin(angle) * radius);
            Vec3 upper = new Vec3(0.5 + Math.cos(angle + 0.9) * radius * 0.45, 5.4 + threatLevel * 0.45,
                    0.5 + Math.sin(angle + 0.9) * radius * 0.45);
            RenderUtils.drawBeam(matrix, buffer, lower, upper, width * 0.22f, color[0], color[1], color[2], 120);
        }
    }

    private void renderNetworkLinks(PsychicBeaconBlockEntity be, int state, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource) {
        if (state == PsychicBeaconBlockEntity.STATE_OFFLINE || state == PsychicBeaconBlockEntity.STATE_FAILED) {
            return;
        }
        if (be.getCachedNetworkNodes().isEmpty()) {
            return;
        }

        long gameTime = be.getLevel().getGameTime();
        float pulse = 0.35f + 0.18f * Mth.sin((gameTime + partialTick) * 0.18f);
        int alpha = (int) (32 + 48 * pulse);
        int[] color = getBeamColor(state, Mth.clamp(be.getThreatLevel(), 0, 4), gameTime, partialTick);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        Vec3 origin = new Vec3(0.5, 1.35, 0.5);

        for (net.minecraft.core.BlockPos node : be.getCachedNetworkNodes()) {
            Vec3 relative = Vec3.atCenterOf(node).add(0, 1.0, 0).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
            RenderUtils.drawBeam(matrix, buffer, origin, relative, 0.0075f, color[0], color[1], color[2], alpha);
        }
    }

    private float getBeamWidth(int state, int threatLevel) {
        float width = 0.085f + threatLevel * 0.035f;
        if (state == PsychicBeaconBlockEntity.STATE_IDLE) {
            return width * 0.72f;
        }
        if (state == PsychicBeaconBlockEntity.STATE_WARNING) {
            return width * 1.18f;
        }
        return width;
    }

    private int[] getBeamColor(int state, int threatLevel, long gameTime, float partialTick) {
        if (state == PsychicBeaconBlockEntity.STATE_WARNING) {
            float pulse = 0.55f + 0.45f * Mth.sin((gameTime + partialTick) * 0.4f);
            return new int[]{255, (int) (58 + 90 * pulse), 24};
        }
        if (state == PsychicBeaconBlockEntity.STATE_IDLE) {
            return new int[]{180, 92, 235};
        }
        return switch (threatLevel) {
            case 0 -> new int[]{198, 86, 236};
            case 1 -> new int[]{116, 132, 255};
            case 2 -> new int[]{44, 220, 255};
            case 3 -> new int[]{255, 190, 68};
            default -> new int[]{255, 64, 112};
        };
    }

    @Override
    public boolean shouldRenderOffScreen(PsychicBeaconBlockEntity be) {
        return true;
    }

    @Override
    public boolean shouldRender(PsychicBeaconBlockEntity be, Vec3 cameraPos) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1.0D, 0.0D, 1.0D)
                .closerThan(cameraPos.multiply(1.0D, 0.0D, 1.0D), this.getViewDistance());
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
