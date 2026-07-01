package com.mymod.flux_turret.network;

import com.mymod.flux_turret.FluxTurretMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        CHANNEL.registerMessage(0, ToggleBeaconPacket.class,
                ToggleBeaconPacket::encode,
                ToggleBeaconPacket::decode,
                ToggleBeaconPacket::handle);
        CHANNEL.registerMessage(1, TurretFirePacket.class,
                TurretFirePacket::encode,
                TurretFirePacket::decode,
                TurretFirePacket::handle);
        CHANNEL.registerMessage(2, SetBeaconBuffPacket.class,
                SetBeaconBuffPacket::encode,
                SetBeaconBuffPacket::decode,
                SetBeaconBuffPacket::handle);
        CHANNEL.registerMessage(3, CycleBeaconDoctrinePacket.class,
                CycleBeaconDoctrinePacket::encode,
                CycleBeaconDoctrinePacket::decode,
                CycleBeaconDoctrinePacket::handle);
    }
}
