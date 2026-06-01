package com.mymod.flux_turret.network;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleBeaconPacket {
    private final BlockPos pos;

    public ToggleBeaconPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ToggleBeaconPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static ToggleBeaconPacket decode(FriendlyByteBuf buf) {
        return new ToggleBeaconPacket(buf.readBlockPos());
    }

    public static void handle(ToggleBeaconPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.level().isLoaded(msg.pos)) return;
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 8 * 8) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof PsychicBeaconBlockEntity beacon) {
                beacon.setEnabled(!beacon.isEnabled());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
