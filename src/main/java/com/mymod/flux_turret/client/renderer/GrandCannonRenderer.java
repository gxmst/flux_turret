package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import com.mymod.flux_turret.client.model.GrandCannonModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GrandCannonRenderer implements BlockEntityRenderer<GrandCannonBlockEntity> {
    private final GeoBlockRenderer<GrandCannonBlockEntity> geckoRenderer;

    public GrandCannonRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new GrandCannonModel());
        this.geckoRenderer.addRenderLayer(new AutoGlowingGeoLayer<>(this.geckoRenderer));
    }

    @Override
    public void render(GrandCannonBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        // Only render from the core block - skip non-core parts
        if (be.getBlockState().getValue(GrandCannonBlock.PART) != GrandCannonBlock.CannonPart.BACK_LEFT) {
            return;
        }

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(GrandCannonBlock.FACING)
                ? state.getValue(GrandCannonBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();

        // The model is centered at origin, barrel points toward -Z (north in model space).
        // The core block is at BACK_LEFT. We need to translate from core block to the center of the 2x2 structure.
        Direction right = facing.getClockWise();
        // Center of 2x2 structure is 0.5 blocks forward + 0.5 blocks right from core
        double centerX = facing.getStepX() * 0.5 + right.getStepX() * 0.5;
        double centerZ = facing.getStepZ() * 0.5 + right.getStepZ() * 0.5;
        poseStack.translate(centerX, 0, centerZ);

        // Model barrel points -Z = NORTH. Rotate to match actual facing.
        float yRot = switch (facing) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case WEST -> -90f;
            case EAST -> 90f;
            default -> 0f;
        };

        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yRot));
        poseStack.translate(-0.5, 0, -0.5);

        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
