package com.mymod.flux_turret.client.renderer;

import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import com.mymod.flux_turret.client.model.EnergyCrystalModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class EnergyCrystalRenderer implements BlockEntityRenderer<EnergyCrystalBlockEntity> {
    private final GeoBlockRenderer<EnergyCrystalBlockEntity> geckoRenderer;

    public EnergyCrystalRenderer(BlockEntityRendererProvider.Context context) {
        this.geckoRenderer = new GeoBlockRenderer<>(new EnergyCrystalModel());
    }

    @Override
    public void render(EnergyCrystalBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        this.geckoRenderer.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }
}
