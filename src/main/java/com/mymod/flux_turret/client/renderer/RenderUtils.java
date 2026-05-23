package com.mymod.flux_turret.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class RenderUtils {
    private RenderUtils() {
    }

    public static void drawBeam(Matrix4f matrix, VertexConsumer buffer, Vec3 start, Vec3 end, float width,
            int r, int g, int b, int a) {
        Vec3 diff = end.subtract(start);
        if (diff.lengthSqr() < 0.0001)
            return;

        Vec3 normal = diff.normalize();
        Vec3 side = normal.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.01)
            side = normal.cross(new Vec3(1, 0, 0));
        side = side.normalize().scale(width);

        Vec3 s1 = start.add(side);
        Vec3 s2 = start.subtract(side);
        Vec3 e1 = end.add(side);
        Vec3 e2 = end.subtract(side);

        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s2.x, (float) s2.y, (float) s2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e2.x, (float) e2.y, (float) e2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e2.x, (float) e2.y, (float) e2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s2.x, (float) s2.y, (float) s2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a).endVertex();
    }

    public static void drawBeam(Matrix4f matrix, VertexConsumer buffer, Vec3 start, Vec3 end,
            float startWidth, float endWidth, int r, int g, int b, int a) {
        Vec3 diff = end.subtract(start);
        Vec3 normal = diff.normalize();
        Vec3 v1 = normal.cross(new Vec3(0, 1, 0));
        if (v1.lengthSqr() < 0.01) {
            v1 = normal.cross(new Vec3(1, 0, 0));
        }
        v1 = v1.normalize();
        Vec3 v2 = normal.cross(v1).normalize();

        drawQuad(matrix, buffer, start, end, v1, startWidth, endWidth, r, g, b, a);
        drawQuad(matrix, buffer, start, end, v2, startWidth, endWidth, r, g, b, a);
    }

    private static void drawQuad(Matrix4f matrix, VertexConsumer buffer, Vec3 start, Vec3 end, Vec3 offsetDir,
            float startWidth, float endWidth, int r, int g, int b, int a) {
        Vec3 s1 = start.add(offsetDir.scale(startWidth));
        Vec3 s2 = start.subtract(offsetDir.scale(startWidth));
        Vec3 e1 = end.add(offsetDir.scale(endWidth));
        Vec3 e2 = end.subtract(offsetDir.scale(endWidth));

        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s2.x, (float) s2.y, (float) s2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e2.x, (float) e2.y, (float) e2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a).endVertex();

        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) e2.x, (float) e2.y, (float) e2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s2.x, (float) s2.y, (float) s2.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a).endVertex();
    }
}
