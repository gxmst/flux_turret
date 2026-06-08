package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.client.model.PsychicBeaconModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PsychicBeaconRenderer implements BlockEntityRenderer<PsychicBeaconBlockEntity> {
    private static final int BEAM_HEIGHT = 192;

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

        BeaconRenderer.renderBeaconBeam(
                poseStack,
                bufferSource,
                BeaconRenderer.BEAM_LOCATION,
                partialTick,
                1.0f,
                be.getLevel().getGameTime(),
                2,
                BEAM_HEIGHT,
                getBeamColor(state, be.getLevel().getGameTime(), partialTick),
                0.18f,
                0.32f);
    }

    private float[] getBeamColor(int state, long gameTime, float partialTick) {
        return switch (state) {
            case PsychicBeaconBlockEntity.STATE_IDLE -> new float[]{0.87f, 0.27f, 0.73f};
            case PsychicBeaconBlockEntity.STATE_ACTIVE -> new float[]{0.82f, 0.08f, 0.20f};
            case PsychicBeaconBlockEntity.STATE_WARNING -> {
                float pulse = 0.55f + 0.45f * Mth.sin((gameTime + partialTick) * 0.4f);
                yield new float[]{1.0f, 0.10f + 0.15f * pulse, 0.04f};
            }
            default -> new float[]{0.87f, 0.27f, 0.73f};
        };
    }

    @Override
    public boolean shouldRenderOffScreen(PsychicBeaconBlockEntity be) {
        return false;
    }

    @Override
    public boolean shouldRender(PsychicBeaconBlockEntity be, Vec3 cameraPos) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1.0D, 0.0D, 1.0D)
                .closerThan(cameraPos.multiply(1.0D, 0.0D, 1.0D), this.getViewDistance());
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
