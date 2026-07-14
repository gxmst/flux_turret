package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mymod.flux_turret.FluxTurretMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Short-lived, client-only range visualization activated from the diagnostics panel. */
@Mod.EventBusSubscriber(modid = FluxTurretMod.MOD_ID, value = Dist.CLIENT)
public final class TurretRangeOverlay {
    private static BlockPos center;
    private static double range;
    private static double minRange;
    private static long expiresAt;
    private static ResourceKey<Level> dimension;

    private TurretRangeOverlay() {
    }

    public static void show(Level level, BlockPos pos, double configuredMinRange, double configuredRange) {
        center = pos.immutable();
        range = Math.max(1.0D, configuredRange);
        minRange = Math.max(0.0D, Math.min(configuredMinRange, range));
        expiresAt = level.getGameTime() + 200L;
        dimension = level.dimension();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (center == null || minecraft.level == null || !minecraft.level.dimension().equals(dimension)
                || minecraft.level.getGameTime() > expiresAt) {
            center = null;
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        double cx = center.getX() + 0.5D;
        double cy = center.getY() + 0.1D;
        double cz = center.getZ() + 0.5D;

        // This is the exact horizontal cross-section of the spherical targeting
        // radius. A distinct inner ring shows the Grand Cannon's blind zone.
        renderCircle(poseStack, consumer, cx, cy, cz, range,
                0.20F, 0.85F, 1.0F, 0.88F);
        if (minRange > 0.0D) {
            renderCircle(poseStack, consumer, cx, cy + 0.015D, cz, minRange,
                    1.0F, 0.35F, 0.22F, 0.92F);
        }
        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void renderCircle(PoseStack poseStack, VertexConsumer consumer,
            double centerX, double y, double centerZ, double radius,
            float red, float green, float blue, float alpha) {
        int segments = Math.max(48, Math.min(160, (int) Math.ceil(radius * 4.0D)));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        for (int segment = 0; segment < segments; segment++) {
            double firstAngle = Math.PI * 2.0D * segment / segments;
            double secondAngle = Math.PI * 2.0D * (segment + 1) / segments;
            float firstX = (float) (centerX + Math.cos(firstAngle) * radius);
            float firstZ = (float) (centerZ + Math.sin(firstAngle) * radius);
            float secondX = (float) (centerX + Math.cos(secondAngle) * radius);
            float secondZ = (float) (centerZ + Math.sin(secondAngle) * radius);
            float normalX = secondX - firstX;
            float normalZ = secondZ - firstZ;
            float length = (float) Math.sqrt(normalX * normalX + normalZ * normalZ);
            if (length > 0.0F) {
                normalX /= length;
                normalZ /= length;
            }
            consumer.vertex(pose, firstX, (float) y, firstZ)
                    .color(red, green, blue, alpha)
                    .normal(normalMatrix, normalX, 0.0F, normalZ).endVertex();
            consumer.vertex(pose, secondX, (float) y, secondZ)
                    .color(red, green, blue, alpha)
                    .normal(normalMatrix, normalX, 0.0F, normalZ).endVertex();
        }
    }
}
