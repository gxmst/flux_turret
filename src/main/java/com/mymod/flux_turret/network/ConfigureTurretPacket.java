package com.mymod.flux_turret.network;

import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import com.mymod.flux_turret.item.TurretUpgradeModuleItem;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.menu.TurretInspectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ConfigureTurretPacket(BlockPos pos, int action) {
    public static final int CYCLE_TARGETING = 0;
    public static final int CYCLE_REDSTONE = 1;
    public static final int CYCLE_ACCESS = 2;
    public static final int CYCLE_WEAPON = 3;
    public static final int CYCLE_UTILITY = 4;
    public static final int RECOVER_MODULES = 5;
    public static final int MANUAL_CRANK = 6;

    public static void encode(ConfigureTurretPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.action);
    }

    public static ConfigureTurretPacket decode(FriendlyByteBuf buffer) {
        return new ConfigureTurretPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(ConfigureTurretPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.action < CYCLE_TARGETING || packet.action > MANUAL_CRANK
                    || player.distanceToSqr(packet.pos.getX() + 0.5D, packet.pos.getY() + 0.5D,
                    packet.pos.getZ() + 0.5D) > 64.0D || !player.level().isLoaded(packet.pos)
                    || !(player.containerMenu instanceof TurretInspectorMenu inspector)) return;

            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (!(blockEntity instanceof TurretBlockEntityBase turret)
                    || !inspector.isInspecting(player, turret)) return;

            if (packet.action == CYCLE_ACCESS) {
                if (!turret.canPlayerChangeAccess(player)) {
                    deny(player);
                    return;
                }
                turret.cycleAccessMode(player);
                return;
            }

            turret.claimIfUnowned(player);
            if (!turret.canPlayerConfigure(player)) {
                deny(player);
                return;
            }

            switch (packet.action) {
                case CYCLE_TARGETING -> turret.cycleTargetingMode();
                case CYCLE_REDSTONE -> turret.cycleRedstoneControlMode();
                case CYCLE_WEAPON -> turret.cycleActiveUpgrade(TurretUpgradeType.Slot.WEAPON);
                case CYCLE_UTILITY -> turret.cycleActiveUpgrade(TurretUpgradeType.Slot.UTILITY);
                case RECOVER_MODULES -> TurretUpgradeModuleItem.recoverAllForPlayer(
                        player.level(), packet.pos, player, turret);
                case MANUAL_CRANK -> manualCrank(player, turret);
                default -> { }
            }
        });
        context.setPacketHandled(true);
    }

    private static void manualCrank(ServerPlayer player, TurretBlockEntityBase turret) {
        if (!(turret instanceof TeslaCoilBlockEntity tesla)) return;
        showManualCrankFailure(player, tesla.tryManualCrank(player));
    }

    public static void showManualCrankFailure(ServerPlayer player,
            TeslaCoilBlockEntity.ManualCrankResult result) {
        String translationKey = result.getFailureTranslationKey();
        if (translationKey != null) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    private static void deny(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.flux_turret.turret_access_denied"), true);
    }
}
