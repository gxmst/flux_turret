package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mymod.flux_turret.FluxTurretMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A short-lived in-world locator for a block reported by a diagnostics screen. */
@Mod.EventBusSubscriber(modid = FluxTurretMod.MOD_ID, value = Dist.CLIENT)
public final class BlockMarkerOverlay {
    private static BlockPos markedPos;
    private static ResourceKey<Level> dimension;
    private static long expiresAt;

    private BlockMarkerOverlay() {
    }

    public static void show(Level level, BlockPos pos) {
        markedPos = pos.immutable();
        dimension = level.dimension();
        expiresAt = level.getGameTime() + 200L;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (markedPos == null || minecraft.level == null
                || !minecraft.level.dimension().equals(dimension)
                || minecraft.level.getGameTime() > expiresAt) {
            markedPos = null;
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        float pulse = 0.72F + 0.28F * Mth.sin(
                (minecraft.level.getGameTime() + event.getPartialTick()) * 0.3F);
        AABB block = new AABB(markedPos).inflate(0.015D);
        LevelRenderer.renderLineBox(poseStack, lines, block,
                1.0F, 0.24F + pulse * 0.25F, 0.12F, 0.95F);
        double x = markedPos.getX() + 0.5D;
        double z = markedPos.getZ() + 0.5D;
        AABB beacon = new AABB(x - 0.015D, markedPos.getY() + 1.0D, z - 0.015D,
                x + 0.015D, markedPos.getY() + 4.0D + pulse, z + 0.015D);
        LevelRenderer.renderLineBox(poseStack, lines, beacon,
                1.0F, 0.55F, 0.16F, 0.72F);
        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
