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
    private final PsychicBeaconBlockEntity beacon;
    private final BlockPos beaconPos;
    private final ContainerData data;

    public PsychicBeaconMenu(int containerId, Inventory playerInventory, PsychicBeaconBlockEntity beacon) {
        super(ModRegistry.PSYCHIC_BEACON_MENU.get(), containerId);
        this.beacon = beacon;
        this.beaconPos = beacon.getBlockPos();
        this.data = new SimpleContainerData(11);
        addDataSlots(data);
    }

    public PsychicBeaconMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ModRegistry.PSYCHIC_BEACON_MENU.get(), containerId);
        this.beacon = null;
        this.beaconPos = buf.readBlockPos();
        this.data = new SimpleContainerData(11);
        addDataSlots(data);
    }

    public int getEnergyStored() {
        return data.get(0);
    }

    public int getMaxEnergy() {
        return data.get(1);
    }

    public int getBeaconState() {
        return data.get(2);
    }

    public int getStability() {
        return data.get(3);
    }

    public int getThreatLevel() {
        return data.get(4);
    }

    public int getTodayKills() {
        return data.get(5);
    }

    public int getTimeUntilDawn() {
        return data.get(6);
    }

    public int getNearbyPrismCount() {
        return data.get(7);
    }

    public int getNearbyTeslaCount() {
        return data.get(8);
    }

    public int getNearbyGatlingCount() {
        return data.get(9);
    }

    public int getEnabled() {
        return data.get(10);
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
        data.set(0, beacon.getEnergyStorage().getEnergyStored());
        data.set(1, beacon.getEnergyStorage().getMaxEnergyStored());
        data.set(2, beacon.getBeaconState());
        data.set(3, beacon.getStability());
        data.set(4, beacon.getThreatLevel());
        data.set(5, beacon.getTodayKills());
        data.set(6, (int) beacon.getTimeUntilDawn());
        int[] cached = beacon.getCachedTurretCounts();
        data.set(7, cached[0]);
        data.set(8, cached[1]);
        data.set(9, cached[2]);
        data.set(10, beacon.isEnabled() ? 1 : 0);
        super.broadcastChanges();
    }
}
