package com.mymod.flux_turret.network;

import com.mymod.flux_turret.FluxTurretMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "3";
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
                ToggleBeaconPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, TurretFirePacket.class,
                TurretFirePacket::encode,
                TurretFirePacket::decode,
                TurretFirePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, SetBeaconBuffPacket.class,
                SetBeaconBuffPacket::encode,
                SetBeaconBuffPacket::decode,
                SetBeaconBuffPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, CycleBeaconDoctrinePacket.class,
                CycleBeaconDoctrinePacket::encode,
                CycleBeaconDoctrinePacket::decode,
                CycleBeaconDoctrinePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
