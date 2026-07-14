package com.mymod.flux_turret.menu;

import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.block.entity.TurretBlockEntityBase;
import com.mymod.flux_turret.network.ConfigureTurretPacket;
import com.mymod.flux_turret.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public class TurretInspectorMenu extends AbstractContainerMenu {
    private static final int DATA_SIZE = 27;
    private static final int ENERGY = 0;
    private static final int MAX_ENERGY = 2;
    private static final int FIRE_COST = 4;
    private static final int DAMAGE_X100 = 6;
    private static final int STATUS = 8;
    private static final int TARGETING = 9;
    private static final int REDSTONE = 10;
    private static final int ACCESS = 11;
    private static final int INSTALLED = 12;
    private static final int ACTIVE_WEAPON = 13;
    private static final int ACTIVE_UTILITY = 14;
    private static final int TARGET_ID = 15;
    private static final int COOLDOWN = 17;
    private static final int RANGE_X100 = 18;
    private static final int CADENCE = 19;
    private static final int TURRET_TYPE = 20;
    private static final int SUPPORT_COUNT = 21;
    private static final int PROGRESS = 22;
    private static final int CAN_CONFIGURE = 23;
    private static final int CAN_CHANGE_ACCESS = 24;
    private static final int HAS_SIGNAL = 25;
    private static final int MIN_RANGE_X100 = 26;

    private final TurretBlockEntityBase turret;
    private final BlockPos turretPos;
    private final Player viewer;
    private final ContainerData data;
    private final String ownerName;

    public TurretInspectorMenu(int containerId, Inventory inventory, TurretBlockEntityBase turret) {
        super(ModRegistry.TURRET_INSPECTOR_MENU.get(), containerId);
        this.turret = turret;
        this.turretPos = turret.getBlockPos();
        this.viewer = inventory.player;
        this.ownerName = turret.getOwnerName();
        this.data = new SimpleContainerData(DATA_SIZE);
        addDataSlots(data);
    }

    public TurretInspectorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModRegistry.TURRET_INSPECTOR_MENU.get(), containerId);
        this.turret = null;
        this.turretPos = buffer.readBlockPos();
        this.ownerName = buffer.readUtf(64);
        this.viewer = inventory.player;
        this.data = new SimpleContainerData(DATA_SIZE);
        addDataSlots(data);
    }

    public static void open(ServerPlayer player, TurretBlockEntityBase turret) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, ignored) -> new TurretInspectorMenu(id, inventory, turret),
                Component.translatable("container.flux_turret.turret_inspector")), buffer -> {
            buffer.writeBlockPos(turret.getBlockPos());
            buffer.writeUtf(turret.getOwnerName(), 64);
        });
    }

    public BlockPos getTurretPos() { return turretPos; }

    /**
     * Server-side packet guard. A matching coordinate alone is not enough: the
     * menu must still be backed by the exact live block entity being changed.
     */
    public boolean isInspecting(Player player, TurretBlockEntityBase candidate) {
        return turret != null && turret == candidate && turretPos.equals(candidate.getBlockPos())
                && stillValid(player);
    }

    public String getOwnerName() {
        if (turret != null) return turret.getOwnerName();
        if (viewer.level().getBlockEntity(turretPos) instanceof TurretBlockEntityBase clientTurret) {
            return clientTurret.getOwnerName();
        }
        return ownerName;
    }
    public int getEnergy() { return getWide(ENERGY); }
    public int getMaxEnergy() { return getWide(MAX_ENERGY); }
    public int getFireCost() { return getWide(FIRE_COST); }
    public double getDamage() { return getWide(DAMAGE_X100) / 100.0D; }
    public int getStatus() { return data.get(STATUS); }
    public int getTargetingMode() { return data.get(TARGETING); }
    public int getRedstoneMode() { return data.get(REDSTONE); }
    public int getAccessMode() { return data.get(ACCESS); }
    public int getInstalledMask() { return data.get(INSTALLED); }
    public int getActiveWeaponMask() { return data.get(ACTIVE_WEAPON); }
    public int getActiveUtilityMask() { return data.get(ACTIVE_UTILITY); }
    public int getTargetId() { return getWide(TARGET_ID); }
    public int getCooldown() { return data.get(COOLDOWN); }
    public double getRange() { return data.get(RANGE_X100) / 100.0D; }
    public int getCadence() { return data.get(CADENCE); }
    public int getTurretType() { return data.get(TURRET_TYPE); }
    public int getSupportCount() { return data.get(SUPPORT_COUNT); }
    public int getProgress() { return data.get(PROGRESS); }
    public boolean canConfigure() { return data.get(CAN_CONFIGURE) != 0; }
    public boolean canChangeAccess() { return data.get(CAN_CHANGE_ACCESS) != 0; }
    public boolean hasSignal() { return data.get(HAS_SIGNAL) != 0; }
    public double getMinRange() { return data.get(MIN_RANGE_X100) / 100.0D; }

    public void sendAction(int action) {
        ModNetworking.CHANNEL.sendToServer(new ConfigureTurretPacket(turretPos, action));
    }

    @Override
    public boolean stillValid(Player player) {
        if (turret == null) return player.distanceToSqr(
                turretPos.getX() + 0.5D, turretPos.getY() + 0.5D, turretPos.getZ() + 0.5D) <= 64.0D;
        return !turret.isRemoved() && turret.getLevel() == player.level()
                && player.distanceToSqr(turretPos.getX() + 0.5D,
                turretPos.getY() + 0.5D, turretPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (turret == null) return;
        setWide(ENERGY, turret.getEnergyStored());
        setWide(MAX_ENERGY, turret.getEnergyCapacity());
        setWide(FIRE_COST, turret.getOperatingEnergyCost());
        setWide(DAMAGE_X100, (int) Math.round(getConfiguredDamage(turret) * 100.0D));
        data.set(STATUS, turret.getOperationalStatus().ordinal());
        data.set(TARGETING, turret.getTargetingMode().ordinal());
        data.set(REDSTONE, turret.getRedstoneControlMode().ordinal());
        data.set(ACCESS, turret.getAccessMode().ordinal());
        data.set(INSTALLED, turret.getInstalledUpgradeMask());
        data.set(ACTIVE_WEAPON, turret.getActiveWeaponUpgradeMask());
        data.set(ACTIVE_UTILITY, turret.getActiveUtilityUpgradeMask());
        setWide(TARGET_ID, turret.getTargetId());
        data.set(COOLDOWN, turret.getAttackCooldown());
        data.set(RANGE_X100, (int) Math.round(turret.getConfiguredRange() * 100.0D));
        data.set(CADENCE, getCadence(turret));
        data.set(TURRET_TYPE, getTurretType(turret));
        data.set(SUPPORT_COUNT, turret instanceof PrismTowerBlockEntity prism ? prism.getSupportCount() : 0);
        data.set(PROGRESS, turret instanceof GatlingTurretBlockEntity gatling ? gatling.getSpinUp() : 0);
        data.set(CAN_CONFIGURE, turret.canPlayerConfigure(viewer) ? 1 : 0);
        data.set(CAN_CHANGE_ACCESS, turret.canPlayerChangeAccess(viewer) ? 1 : 0);
        data.set(HAS_SIGNAL, turret.hasRedstoneSignal() ? 1 : 0);
        data.set(MIN_RANGE_X100, turret instanceof GrandCannonBlockEntity
                ? (int) Math.round(TurretConfig.GRAND_CANNON_MIN_RANGE.get() * 100.0D) : 0);
        super.broadcastChanges();
    }

    private static int getTurretType(TurretBlockEntityBase turret) {
        if (turret instanceof GatlingTurretBlockEntity) return 0;
        if (turret instanceof TeslaCoilBlockEntity) return 1;
        if (turret instanceof PrismTowerBlockEntity) return 2;
        if (turret instanceof GrandCannonBlockEntity) return 3;
        return 0;
    }

    private static double getConfiguredDamage(TurretBlockEntityBase turret) {
        if (turret instanceof GatlingTurretBlockEntity) return TurretConfig.GATLING_DAMAGE.get();
        if (turret instanceof TeslaCoilBlockEntity) return TurretConfig.TESLA_DAMAGE.get();
        if (turret instanceof PrismTowerBlockEntity prism) {
            return TurretConfig.PRISM_DAMAGE.get() * (1.0D + prism.getSupportCount() * 0.35D);
        }
        return TurretConfig.GRAND_CANNON_DAMAGE.get();
    }

    private static int getCadence(TurretBlockEntityBase turret) {
        if (turret instanceof GatlingTurretBlockEntity gatling) return gatling.getCurrentFireInterval();
        if (turret instanceof TeslaCoilBlockEntity) return 24;
        if (turret instanceof PrismTowerBlockEntity) return 30;
        return TurretConfig.GRAND_CANNON_COOLDOWN.get();
    }

    private int getWide(int index) {
        return (data.get(index) & 0xFFFF) | ((data.get(index + 1) & 0xFFFF) << 16);
    }

    private void setWide(int index, int value) {
        data.set(index, value & 0xFFFF);
        data.set(index + 1, (value >>> 16) & 0xFFFF);
    }
}
