package com.mymod.flux_turret.client.screen;

import com.mymod.flux_turret.block.entity.RedstoneControlMode;
import com.mymod.flux_turret.block.entity.TargetingMode;
import com.mymod.flux_turret.block.entity.TurretAccessMode;
import com.mymod.flux_turret.block.entity.TurretStatus;
import com.mymod.flux_turret.client.renderer.TurretRangeOverlay;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.menu.TurretInspectorMenu;
import com.mymod.flux_turret.network.ConfigureTurretPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class TurretInspectorScreen extends AbstractContainerScreen<TurretInspectorMenu> {
    private static final int PANEL = 0xF010151D;
    private static final int PANEL_SOFT = 0xE019202A;
    private static final int BORDER = 0xFF3FD6FF;
    private static final int TEXT = 0xFFEAF2F8;
    private static final int DIM = 0xFF9EAAB8;
    private static final int CYAN = 0xFF36D9FF;
    private static final int GREEN = 0xFF72E68A;
    private static final int AMBER = 0xFFFFC45A;
    private static final int RED = 0xFFFF6575;

    private Button targetingButton;
    private Button redstoneButton;
    private Button accessButton;
    private Button weaponButton;
    private Button utilityButton;
    private Button recoverButton;
    private Button crankButton;

    public TurretInspectorScreen(TurretInspectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 264;
        imageHeight = 224;
        inventoryLabelY = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        targetingButton = addRenderableWidget(actionButton(x + 14, y + 124, 112,
                ConfigureTurretPacket.CYCLE_TARGETING,
                "screen.flux_turret.inspector.targeting_help"));
        redstoneButton = addRenderableWidget(actionButton(x + 138, y + 124, 112,
                ConfigureTurretPacket.CYCLE_REDSTONE,
                "screen.flux_turret.inspector.redstone_help"));
        weaponButton = addRenderableWidget(actionButton(x + 14, y + 150, 112,
                ConfigureTurretPacket.CYCLE_WEAPON,
                "screen.flux_turret.inspector.weapon_help"));
        utilityButton = addRenderableWidget(actionButton(x + 138, y + 150, 112,
                ConfigureTurretPacket.CYCLE_UTILITY,
                "screen.flux_turret.inspector.utility_help"));
        accessButton = addRenderableWidget(actionButton(x + 14, y + 176, 76,
                ConfigureTurretPacket.CYCLE_ACCESS,
                "screen.flux_turret.inspector.access_help"));
        recoverButton = addRenderableWidget(actionButton(x + 96, y + 176, 76,
                ConfigureTurretPacket.RECOVER_MODULES,
                "screen.flux_turret.inspector.recover_help"));
        crankButton = addRenderableWidget(actionButton(x + 178, y + 176, 72,
                ConfigureTurretPacket.MANUAL_CRANK,
                "screen.flux_turret.inspector.crank_help"));
        addRenderableWidget(Button.builder(Component.translatable("screen.flux_turret.inspector.range"), button -> {
                    if (minecraft != null && minecraft.level != null) {
                        TurretRangeOverlay.show(minecraft.level, menu.getTurretPos(),
                                menu.getMinRange(), menu.getRange());
                    }
                }).bounds(x + 178, y + 202, 72, 16)
                .tooltip(Tooltip.create(Component.translatable("screen.flux_turret.inspector.range_help")))
                .build());
    }

    private Button actionButton(int x, int y, int width, int action, String tooltipKey) {
        return Button.builder(Component.empty(), button -> menu.sendAction(action))
                .bounds(x, y, width, 18)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        TurretStatus status = TurretStatus.fromOrdinal(menu.getStatus());
        int statusColor = switch (status) {
            case FIRING, TRACKING -> GREEN;
            case WARMING_UP, COOLDOWN, NO_TARGET -> AMBER;
            case NO_ENERGY, REDSTONE_STOP, STRUCTURE_INVALID -> RED;
        };

        graphics.drawString(font, turretName(), x + 18, y + 12, TEXT, false);
        graphics.drawString(font, Component.translatable(status.getTranslationKey()), x + 18, y + 31,
                statusColor, false);
        graphics.drawString(font, Component.translatable("screen.flux_turret.inspector.owner",
                menu.getOwnerName().isBlank()
                        ? Component.translatable("screen.flux_turret.inspector.unowned")
                        : Component.literal(menu.getOwnerName())), x + 138, y + 31, DIM, false);

        drawEnergy(graphics, x + 14, y + 49, 236, 13);
        drawMetric(graphics, x + 14, y + 71, 74,
                Component.translatable("screen.flux_turret.inspector.range_label"),
                Component.literal(menu.getMinRange() > 0.0D
                        ? String.format("%.1f–%.1f", menu.getMinRange(), menu.getRange())
                        : String.format("%.1f", menu.getRange())));
        drawMetric(graphics, x + 95, y + 71, 74,
                Component.translatable("screen.flux_turret.inspector.damage"),
                Component.literal(String.format("%.1f", menu.getDamage())));
        drawMetric(graphics, x + 176, y + 71, 74,
                Component.translatable("screen.flux_turret.inspector.cadence"),
                Component.translatable("screen.flux_turret.inspector.ticks", menu.getCadence()));

        int shots = menu.getFireCost() <= 0 ? 0 : menu.getEnergy() / menu.getFireCost();
        graphics.drawString(font, Component.translatable("screen.flux_turret.inspector.details",
                        formatFe(menu.getFireCost()), shots, menu.getCooldown()),
                x + 18, y + 105, DIM, false);
        if (menu.getTurretType() == 2) {
            graphics.drawString(font, Component.translatable("screen.flux_turret.inspector.supports",
                    menu.getSupportCount()), x + 177, y + 105, CYAN, false);
        } else if (menu.getTurretType() == 0) {
            graphics.drawString(font, Component.translatable("screen.flux_turret.inspector.spin",
                    Mth.clamp(menu.getProgress() / 2, 0, 100)), x + 177, y + 105, CYAN, false);
        }

        graphics.drawString(font, Component.translatable("screen.flux_turret.inspector.signal",
                        Component.translatable(menu.hasSignal() ? "options.on" : "options.off")),
                x + 14, y + 207, DIM, false);
    }

    private void updateButtons() {
        boolean canConfigure = menu.canConfigure();
        TargetingMode targeting = TargetingMode.fromOrdinal(menu.getTargetingMode());
        RedstoneControlMode redstone = RedstoneControlMode.fromOrdinal(menu.getRedstoneMode());
        TurretAccessMode access = TurretAccessMode.fromOrdinal(menu.getAccessMode());
        targetingButton.setMessage(Component.translatable("screen.flux_turret.inspector.targeting",
                Component.translatable(targeting.getTranslationKey())));
        redstoneButton.setMessage(Component.translatable("screen.flux_turret.inspector.redstone",
                Component.translatable(redstone.getTranslationKey())));
        accessButton.setMessage(Component.translatable(access.getTranslationKey()));
        weaponButton.setMessage(activeModuleLabel("screen.flux_turret.inspector.weapon",
                menu.getActiveWeaponMask()));
        utilityButton.setMessage(activeModuleLabel("screen.flux_turret.inspector.utility",
                menu.getActiveUtilityMask()));
        weaponButton.setTooltip(Tooltip.create(installedModuleTooltip(
                TurretUpgradeType.Slot.WEAPON, menu.getActiveWeaponMask())));
        utilityButton.setTooltip(Tooltip.create(installedModuleTooltip(
                TurretUpgradeType.Slot.UTILITY, menu.getActiveUtilityMask())));
        recoverButton.setMessage(Component.translatable("screen.flux_turret.inspector.recover"));
        crankButton.setMessage(Component.translatable("screen.flux_turret.inspector.crank"));

        targetingButton.active = canConfigure;
        redstoneButton.active = canConfigure;
        accessButton.active = menu.canChangeAccess();
        weaponButton.active = canConfigure && hasSlotModule(TurretUpgradeType.Slot.WEAPON);
        utilityButton.active = canConfigure && hasSlotModule(TurretUpgradeType.Slot.UTILITY);
        recoverButton.active = canConfigure && menu.getInstalledMask() != 0;
        crankButton.visible = menu.getTurretType() == 1;
        crankButton.active = canConfigure;
    }

    private boolean hasSlotModule(TurretUpgradeType.Slot slot) {
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if (type.getSlot() == slot && (menu.getInstalledMask() & type.getMask()) != 0) return true;
        }
        return false;
    }

    private Component activeModuleLabel(String labelKey, int mask) {
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if ((mask & type.getMask()) != 0) {
                return Component.translatable(labelKey,
                        Component.translatable("item.flux_turret." + type.getId() + "_module"));
            }
        }
        return Component.translatable(labelKey,
                Component.translatable("screen.flux_turret.inspector.none").withStyle(ChatFormatting.DARK_GRAY));
    }

    private Component installedModuleTooltip(TurretUpgradeType.Slot slot, int activeMask) {
        net.minecraft.network.chat.MutableComponent tooltip = Component.translatable(
                slot == TurretUpgradeType.Slot.WEAPON
                        ? "screen.flux_turret.inspector.weapon_help"
                        : "screen.flux_turret.inspector.utility_help").copy();
        boolean found = false;
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            if (type.getSlot() != slot || (menu.getInstalledMask() & type.getMask()) == 0) continue;
            found = true;
            tooltip.append("\n").append(Component.literal((activeMask & type.getMask()) != 0 ? "[x] " : "[ ] ")
                    .withStyle((activeMask & type.getMask()) != 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.translatable("item.flux_turret." + type.getId() + "_module"));
        }
        if (!found) tooltip.append("\n").append(Component.translatable(
                "screen.flux_turret.inspector.none").withStyle(ChatFormatting.DARK_GRAY));
        return tooltip;
    }

    private Component turretName() {
        return Component.translatable(switch (menu.getTurretType()) {
            case 1 -> "block.flux_turret.tesla_coil";
            case 2 -> "block.flux_turret.prism_tower";
            case 3 -> "block.flux_turret.grand_cannon";
            default -> "block.flux_turret.gatling_turret";
        });
    }

    private void drawEnergy(GuiGraphics graphics, int x, int y, int width, int height) {
        float ratio = menu.getMaxEnergy() <= 0 ? 0.0F
                : Mth.clamp((float) menu.getEnergy() / menu.getMaxEnergy(), 0.0F, 1.0F);
        graphics.fill(x, y, x + width, y + height, 0xFF111722);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF202A35);
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * ratio), y + height - 1,
                ratio > 0.2F ? CYAN : RED);
        String text = formatFe(menu.getEnergy()) + " / " + formatFe(menu.getMaxEnergy()) + " FE";
        graphics.drawCenteredString(font, text, x + width / 2, y + 3, TEXT);
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, int width, Component label, Component value) {
        graphics.fill(x, y, x + width, y + 27, PANEL_SOFT);
        graphics.drawString(font, label, x + 6, y + 4, DIM, false);
        graphics.drawString(font, value, x + 6, y + 16, CYAN, false);
    }

    private String formatFe(int value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0D);
        if (value >= 10_000) return Math.round(value / 1_000.0F) + "k";
        return String.format("%,d", value);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x + 4, y + 5, x + imageWidth + 4, y + imageHeight + 5, 0x70000000);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        graphics.fill(x, y, x + imageWidth, y + 42, 0xFF1A2330);
        graphics.fill(x, y + 41, x + imageWidth, y + 42, 0x803FD6FF);
        graphics.fill(x, y, x + imageWidth, y + 1, BORDER);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BORDER);
        graphics.fill(x, y, x + 1, y + imageHeight, BORDER);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
