package com.mymod.flux_turret.network;

import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import com.mymod.flux_turret.util.TurretVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightweight client-bound packet emitted once per shot. Drives the firing
 * visuals (beam window + animation countdown) without forcing a full block
 * entity sync on every shot.
 *
 * <p>It carries the server's authoritative target at fire time, so the visuals
 * stay correct even if this packet arrives before the next block-entity sync
 * (e.g. the turret switched target and fired on the same tick).
 */
public class TurretFirePacket {
    private static final int MAX_EFFECT_POINTS = 8;

    private final BlockPos pos;
    private final long firedAtGameTime;
    private final int targetId;
    private final int targetType;
    @Nullable
    private final BlockPos targetPos;
    @Nullable
    private final Vec3 impactPos;
    private final List<Vec3> secondaryEffectPoints;
    private final int effectFlags;
    private final float effectStrength;

    public TurretFirePacket(BlockPos pos, long firedAtGameTime,
                            int targetId, int targetType, @Nullable BlockPos targetPos,
                            @Nullable Vec3 impactPos, List<Vec3> secondaryEffectPoints,
                            int effectFlags, float effectStrength) {
        this.pos = pos;
        this.firedAtGameTime = Math.max(0L, firedAtGameTime);
        this.targetId = targetId;
        this.targetType = targetType;
        this.targetPos = targetPos;
        this.impactPos = impactPos;
        this.secondaryEffectPoints = List.copyOf(
                secondaryEffectPoints.subList(0, Math.min(MAX_EFFECT_POINTS, secondaryEffectPoints.size())));
        this.effectFlags = effectFlags;
        this.effectStrength = Float.isFinite(effectStrength) ? effectStrength : 0.0f;
    }

    public static void encode(TurretFirePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarLong(msg.firedAtGameTime);
        buf.writeVarInt(msg.targetId);
        buf.writeByte(msg.targetType);
        buf.writeBoolean(msg.targetPos != null);
        if (msg.targetPos != null) {
            buf.writeBlockPos(msg.targetPos);
        }
        buf.writeBoolean(msg.impactPos != null);
        if (msg.impactPos != null) {
            writeRelativeVec(buf, msg.pos, msg.impactPos);
        }
        buf.writeByte(msg.secondaryEffectPoints.size());
        for (Vec3 point : msg.secondaryEffectPoints) {
            writeRelativeVec(buf, msg.pos, point);
        }
        buf.writeVarInt(msg.effectFlags);
        buf.writeFloat(msg.effectStrength);
    }

    public static TurretFirePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long firedAtGameTime = buf.readVarLong();
        int targetId = buf.readVarInt();
        int targetType = buf.readByte();
        BlockPos targetPos = buf.readBoolean() ? buf.readBlockPos() : null;
        Vec3 impactPos = buf.readBoolean() ? readRelativeVec(buf, pos) : null;
        int pointCount = buf.readUnsignedByte();
        if (pointCount > MAX_EFFECT_POINTS) {
            throw new IllegalArgumentException("Too many turret effect points: " + pointCount);
        }
        List<Vec3> secondaryEffectPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            secondaryEffectPoints.add(readRelativeVec(buf, pos));
        }
        int effectFlags = buf.readVarInt();
        float effectStrength = buf.readFloat();
        return new TurretFirePacket(pos, firedAtGameTime, targetId, targetType, targetPos, impactPos,
                secondaryEffectPoints, effectFlags, effectStrength);
    }

    private static void writeRelativeVec(FriendlyByteBuf buf, BlockPos origin, Vec3 point) {
        buf.writeFloat((float) (point.x - origin.getX()));
        buf.writeFloat((float) (point.y - origin.getY()));
        buf.writeFloat((float) (point.z - origin.getZ()));
    }

    private static Vec3 readRelativeVec(FriendlyByteBuf buf, BlockPos origin) {
        return new Vec3(
                origin.getX() + buf.readFloat(),
                origin.getY() + buf.readFloat(),
                origin.getZ() + buf.readFloat());
    }

    public static void handle(TurretFirePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(msg)));
        ctx.get().setPacketHandled(true);
    }

    /** Isolated so the server class loader never touches client-only classes. */
    private static final class ClientHandler {
        static void apply(TurretFirePacket msg) {
            Level level = Minecraft.getInstance().level;
            if (level == null) return;
            BlockEntity be = level.getBlockEntity(msg.pos);
            if (be instanceof TurretBlockEntityBase turret) {
                turret.onClientFire(msg.firedAtGameTime, msg.targetId, msg.targetType, msg.targetPos);
                if (msg.impactPos != null) {
                    turret.visualCachedTargetPos = msg.impactPos;
                }
                TurretVisualEffects.handleClientFire(turret, msg.impactPos,
                        msg.secondaryEffectPoints, msg.effectFlags, msg.effectStrength);
            }
        }
    }
}
