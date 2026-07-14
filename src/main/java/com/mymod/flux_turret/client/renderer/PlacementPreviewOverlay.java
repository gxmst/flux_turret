package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.block.GrandCannonBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the complete reserved footprint before a multi-block turret or beacon
 * is placed. Green cells are replaceable; red cells identify the obstruction
 * that would make placement fail.
 */
@Mod.EventBusSubscriber(modid = FluxTurretMod.MOD_ID, value = Dist.CLIENT)
public final class PlacementPreviewOverlay {
    private PlacementPreviewOverlay() {
    }

    @SubscribeEvent
    public static void renderPlacementPreview(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) return;

        InteractionHand hand = previewHand(player);
        if (hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = event.getTarget();
        BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
        BlockPos origin = context.getClickedPos();
        List<BlockPos> footprint = footprint(stack, context, origin);
        if (footprint.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        Vec3 camera = event.getCamera().getPosition();
        for (BlockPos pos : footprint) {
            boolean replaceable = pos.getY() >= level.getMinBuildHeight()
                    && pos.getY() < level.getMaxBuildHeight()
                    && level.getBlockState(pos).canBeReplaced(context);
            float red = replaceable ? 0.18F : 1.0F;
            float green = replaceable ? 0.95F : 0.18F;
            float blue = replaceable ? 0.42F : 0.12F;
            AABB outline = new AABB(pos).inflate(0.003D)
                    .move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(poseStack, lines, outline,
                    red, green, blue, 0.82F);
        }
    }

    private static InteractionHand previewHand(Player player) {
        if (isPreviewItem(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isPreviewItem(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean isPreviewItem(ItemStack stack) {
        return stack.is(ModRegistry.TESLA_COIL_ITEM.get())
                || stack.is(ModRegistry.PRISM_TOWER_ITEM.get())
                || stack.is(ModRegistry.GRAND_CANNON_ITEM.get())
                || stack.is(ModRegistry.PSYCHIC_BEACON_ITEM.get());
    }

    private static List<BlockPos> footprint(
            ItemStack stack, BlockPlaceContext context, BlockPos origin) {
        if (stack.is(ModRegistry.TESLA_COIL_ITEM.get())
                || stack.is(ModRegistry.PRISM_TOWER_ITEM.get())) {
            return List.of(origin, origin.above(), origin.above(2));
        }
        if (stack.is(ModRegistry.PSYCHIC_BEACON_ITEM.get())) {
            return List.of(origin, origin.above());
        }
        if (stack.is(ModRegistry.GRAND_CANNON_ITEM.get())) {
            Direction facing = context.getHorizontalDirection();
            List<BlockPos> positions = new ArrayList<>(GrandCannonBlock.CannonPart.values().length);
            for (GrandCannonBlock.CannonPart part : GrandCannonBlock.CannonPart.values()) {
                positions.add(part.offset(origin, facing));
            }
            return positions;
        }
        return List.of();
    }
}
