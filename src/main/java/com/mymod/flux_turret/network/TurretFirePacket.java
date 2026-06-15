package com.mymod.flux_turret.network;

import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

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
    private final BlockPos pos;
    private final int targetId;
    private final int targetType;
    @Nullable
    private final BlockPos targetPos;

    public TurretFirePacket(BlockPos pos, int targetId, int targetType, @Nullable BlockPos targetPos) {
        this.pos = pos;
        this.targetId = targetId;
        this.targetType = targetType;
        this.targetPos = targetPos;
    }

    public static void encode(TurretFirePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.targetId);
        buf.writeByte(msg.targetType);
        buf.writeBoolean(msg.targetPos != null);
        if (msg.targetPos != null) {
            buf.writeBlockPos(msg.targetPos);
        }
    }

    public static TurretFirePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int targetId = buf.readInt();
        int targetType = buf.readByte();
        BlockPos targetPos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new TurretFirePacket(pos, targetId, targetType, targetPos);
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
                turret.onClientFire(level.getGameTime(), msg.targetId, msg.targetType, msg.targetPos);
            }
        }
    }
}
