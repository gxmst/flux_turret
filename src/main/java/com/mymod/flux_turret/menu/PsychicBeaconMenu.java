package com.mymod.flux_turret.menu;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.network.ModNetworking;
import com.mymod.flux_turret.network.ToggleBeaconPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class PsychicBeaconMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 13;
    private static final int ENERGY_LOW = 0;
    private static final int ENERGY_HIGH = 1;
    private static final int MAX_ENERGY_LOW = 2;
    private static final int MAX_ENERGY_HIGH = 3;
    private static final int BEACON_STATE = 4;
    private static final int STABILITY = 5;
    private static final int THREAT_LEVEL = 6;
    private static final int TODAY_KILLS = 7;
    private static final int TIME_UNTIL_DAWN = 8;
    private static final int NEARBY_PRISM = 9;
    private static final int NEARBY_TESLA = 10;
    private static final int NEARBY_GATLING = 11;
    private static final int ENABLED = 12;

    private final PsychicBeaconBlockEntity beacon;
    private final BlockPos beaconPos;
    private final ContainerData data;

    public PsychicBeaconMenu(int containerId, Inventory playerInventory, PsychicBeaconBlockEntity beacon) {
        super(ModRegistry.PSYCHIC_BEACON_MENU.get(), containerId);
        this.beacon = beacon;
        this.beaconPos = beacon.getBlockPos();
        this.data = new SimpleContainerData(DATA_SIZE);
        addDataSlots(data);
    }

    public PsychicBeaconMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ModRegistry.PSYCHIC_BEACON_MENU.get(), containerId);
        this.beacon = null;
        this.beaconPos = buf.readBlockPos();
        this.data = new SimpleContainerData(DATA_SIZE);
        addDataSlots(data);
    }

    public int getEnergyStored() {
        return getWideValue(ENERGY_LOW);
    }

    public int getMaxEnergy() {
        return getWideValue(MAX_ENERGY_LOW);
    }

    public int getBeaconState() {
        return data.get(BEACON_STATE);
    }

    public int getStability() {
        return data.get(STABILITY);
    }

    public int getThreatLevel() {
        return data.get(THREAT_LEVEL);
    }

    public int getTodayKills() {
        return data.get(TODAY_KILLS);
    }

    public int getTimeUntilDawn() {
        return data.get(TIME_UNTIL_DAWN);
    }

    public int getNearbyPrismCount() {
        return data.get(NEARBY_PRISM);
    }

    public int getNearbyTeslaCount() {
        return data.get(NEARBY_TESLA);
    }

    public int getNearbyGatlingCount() {
        return data.get(NEARBY_GATLING);
    }

    public int getEnabled() {
        return data.get(ENABLED);
    }

    public PsychicBeaconBlockEntity getBeacon() {
        return beacon;
    }

    public void toggleEnabled() {
        ModNetworking.CHANNEL.sendToServer(new ToggleBeaconPacket(beaconPos));
    }

    @Override
    public boolean stillValid(Player player) {
        if (beacon == null) return true;
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(beacon.getLevel(), beacon.getBlockPos()), player, ModRegistry.PSYCHIC_BEACON_BLOCK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (beacon == null) return;
        setWideValue(ENERGY_LOW, beacon.getEnergyStorage().getEnergyStored());
        setWideValue(MAX_ENERGY_LOW, beacon.getEnergyStorage().getMaxEnergyStored());
        data.set(BEACON_STATE, beacon.getBeaconState());
        data.set(STABILITY, beacon.getStability());
        data.set(THREAT_LEVEL, beacon.getThreatLevel());
        data.set(TODAY_KILLS, beacon.getTodayKills());
        data.set(TIME_UNTIL_DAWN, (int) beacon.getTimeUntilDawn());
        int[] cached = beacon.getCachedTurretCounts();
        data.set(NEARBY_PRISM, cached[0]);
        data.set(NEARBY_TESLA, cached[1]);
        data.set(NEARBY_GATLING, cached[2]);
        data.set(ENABLED, beacon.isEnabled() ? 1 : 0);
        super.broadcastChanges();
    }

    private int getWideValue(int lowIndex) {
        return (data.get(lowIndex) & 0xFFFF) | ((data.get(lowIndex + 1) & 0xFFFF) << 16);
    }

    private void setWideValue(int lowIndex, int value) {
        data.set(lowIndex, value & 0xFFFF);
        data.set(lowIndex + 1, (value >>> 16) & 0xFFFF);
    }
}
