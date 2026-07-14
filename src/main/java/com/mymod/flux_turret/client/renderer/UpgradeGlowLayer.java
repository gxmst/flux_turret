package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import com.mymod.flux_turret.client.TurretClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Extra emissive pass drawn only when a turret has at least one upgrade module
 * installed, giving upgraded turrets a slow "breathing" glow so players can tell
 * at a glance that a turret has been enhanced.
 *
 * <p>Reuses the turret's existing auto-glow ({@code _glowmask}) texture rather
 * than a dedicated mask, so no new art is required — it re-renders the emissive
 * layer a second time with a time-varying alpha on top of GeckoLib's own
 * {@link software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer AutoGlowingGeoLayer},
 * intensifying the glow instead of adding new lit regions.
 *
 * <p>Client-only. {@code upgradeMask} is synced to the client via the base block
 * entity save, so {@link TurretBlockEntityBase#hasAnyUpgrade()} is valid here.
 */
public class UpgradeGlowLayer<T extends TurretBlockEntityBase> extends GeoRenderLayer<T> {
    // Pulse the added glow between these alpha bounds so the effect reads as a
    // living "powered up" shimmer rather than a flat brightness bump.
    private static final float MIN_ALPHA = 0.15f;
    private static final float MAX_ALPHA = 0.55f;
    private static final float PULSE_SPEED = 0.08f;

    public UpgradeGlowLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
            int packedLight, int packedOverlay) {
        if (!animatable.hasAnyUpgrade()) {
            return;
        }
        if (animatable.getLevel() == null) {
            return;
        }
        double renderDistance = TurretClientConfig.upgradeGlowDistance();
        if (renderDistance <= 0.0) {
            return;
        }
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (Vec3.atCenterOf(animatable.getBlockPos()).distanceToSqr(cameraPos) > renderDistance * renderDistance) {
            return;
        }

        double time = (animatable.getLevel().getGameTime() + partialTick) * PULSE_SPEED;
        float pulse = (float) ((Math.sin(time) + 1.0) * 0.5); // 0..1
        float alpha = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * pulse;

        RenderType emissive = AutoGlowingTexture.getRenderType(getTextureResource(animatable));
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, emissive,
                bufferSource.getBuffer(emissive), partialTick, LightTexture.FULL_SKY, OverlayTexture.NO_OVERLAY,
                1, 1, 1, alpha);
    }
}
