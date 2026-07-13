package com.mymod.flux_turret.network;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetBeaconBuffPacket {
    private final BlockPos pos;
    private final int buffIndex;

    public SetBeaconBuffPacket(BlockPos pos, int buffIndex) {
        this.pos = pos;
        this.buffIndex = buffIndex;
    }

    public static void encode(SetBeaconBuffPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.buffIndex);
    }

    public static SetBeaconBuffPacket decode(FriendlyByteBuf buf) {
        return new SetBeaconBuffPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(SetBeaconBuffPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.level().isLoaded(msg.pos)) return;
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 8 * 8) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof PsychicBeaconBlockEntity beacon) {
                if (!beacon.canPlayerConfigure(player)) return;
                if (beacon.isBattleInProgress()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.flux_turret.beacon_settings_locked"), true);
                    return;
                }
                beacon.toggleSelectedBuff(msg.buffIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
