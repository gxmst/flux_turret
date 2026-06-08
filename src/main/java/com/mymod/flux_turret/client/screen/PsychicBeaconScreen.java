package com.mymod.flux_turret.client.screen;

import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.menu.PsychicBeaconMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class PsychicBeaconScreen extends AbstractContainerScreen<PsychicBeaconMenu> {
    private static final int PANEL = 0xF00D1118;
    private static final int PANEL_SOFT = 0xE0141822;
    private static final int HEADER = 0xFF211529;
    private static final int BORDER = 0xFF8D5AC7;
    private static final int BORDER_SOFT = 0x506E50A3;
    private static final int TEXT = 0xFFE7E1EC;
    private static final int TEXT_DIM = 0xFFAFA7B8;
    private static final int CYAN = 0xFF31D7FF;
    private static final int GREEN = 0xFF72E68A;
    private static final int AMBER = 0xFFFFC45A;
    private static final int RED = 0xFFFF5B6B;

    private Button toggleButton;

    public PsychicBeaconScreen(PsychicBeaconMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 238;
        this.imageHeight = 234;
        this.inventoryLabelY = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        toggleButton = addRenderableWidget(Button.builder(
                Component.literal(""),
                b -> menu.toggleEnabled())
                .bounds(x + this.imageWidth - 66, y + 8, 56, 18)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x + 3, y + 4, x + this.imageWidth + 3, y + this.imageHeight + 4, 0x70000000);
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        guiGraphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 30, HEADER);
        guiGraphics.fill(x + 1, y + 30, x + this.imageWidth - 1, y + 31, BORDER_SOFT);
        drawBorder(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER);
        drawBorder(guiGraphics, x + 1, y + 1, this.imageWidth - 2, this.imageHeight - 2, BORDER_SOFT);

        renderEnergyBar(guiGraphics, x + 12, y + 46, this.imageWidth - 24, 12);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (toggleButton != null) {
            boolean enabled = menu.getEnabled() == 1;
            toggleButton.setMessage(Component.literal(enabled ? "启用" : "关闭"));
        }

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int state = menu.getBeaconState();
        int stateColor = getStateColor(state, partialTick);

        guiGraphics.fill(x + 10, y + 10, x + 18, y + 18, 0xFF2C3038);
        guiGraphics.fill(x + 11, y + 11, x + 17, y + 17, stateColor);
        drawText(guiGraphics, Component.translatable("container.flux_turret.psychic_beacon").getString(),
                x + 24, y + 9, 140, 0xFFECD7FF);

        int energy = menu.getEnergyStored();
        int maxEnergy = menu.getMaxEnergy();
        int drainRate = TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get();
        drawText(guiGraphics, "心灵能量", x + 13, y + 34, 80, TEXT_DIM);
        drawText(guiGraphics,
                formatFe(energy) + " / " + formatFe(maxEnergy) + " FE  维护 " + formatFe(drainRate) + "/t",
                x + 13, y + 61, this.imageWidth - 26, getEnergyColor(energy, maxEnergy));

        int cardW = 103;
        int left = x + 12;
        int right = left + cardW + 8;
        int row1 = y + 80;
        int row2 = row1 + 44;
        int row3 = row2 + 44;

        int stability = menu.getStability();
        int stabilityMax = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        int threatLevel = menu.getThreatLevel();
        int kills = menu.getTodayKills();
        int minKills = TurretConfig.PSYCHIC_BEACON_MIN_KILLS.get();
        int dawnCost = TurretConfig.PSYCHIC_BEACON_DAWN_COST.get();

        drawMetric(guiGraphics, left, row1, cardW, 36,
                "状态", getStateText(state), stateColor, stateColor);
        drawMetric(guiGraphics, right, row1, cardW, 36,
                "稳定度", stability + " / " + stabilityMax, getStabilityColor(stability, stabilityMax), getStabilityColor(stability, stabilityMax));

        drawMetric(guiGraphics, left, row2, cardW, 36,
                "威胁等级", "Lv." + threatLevel + "  半径 " + ((threatLevel + 1) * 10), getThreatColor(threatLevel), getThreatColor(threatLevel));
        drawMetric(guiGraphics, right, row2, cardW, 36,
                "今日净化", kills + " / " + minKills, kills >= minKills ? GREEN : RED, kills >= minKills ? GREEN : RED);

        drawMetric(guiGraphics, left, row3, cardW, 36,
                "清晨合成", formatTicks(menu.getTimeUntilDawn()), CYAN, TEXT);
        drawMetric(guiGraphics, right, row3, cardW, 36,
                "合成能耗", formatFe(dawnCost) + " FE", energy >= dawnCost ? GREEN : AMBER, energy >= dawnCost ? GREEN : AMBER);

        drawNetworkPanel(guiGraphics, x + 12, y + 204, this.imageWidth - 24, 18);
    }

    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int energy = menu.getEnergyStored();
        int maxEnergy = menu.getMaxEnergy();
        float ratio = maxEnergy > 0 ? Mth.clamp((float) energy / maxEnergy, 0.0f, 1.0f) : 0.0f;
        int filled = Math.min(width - 2, Math.round((width - 2) * ratio));
        int fillColor = getEnergyColor(energy, maxEnergy);

        guiGraphics.fill(x, y, x + width, y + height, 0xFF252A33);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF10141C);
        guiGraphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        try {
            if (filled > 0) {
                guiGraphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fillColor);
                guiGraphics.fill(x + 1, y + 1, x + 1 + filled, y + 4, 0x55FFFFFF);
            }
        } finally {
            guiGraphics.disableScissor();
        }
        drawBorder(guiGraphics, x, y, width, height, 0xFF3B4454);
    }

    private void drawMetric(GuiGraphics guiGraphics, int x, int y, int width, int height,
            String label, String value, int accent, int valueColor) {
        guiGraphics.fill(x, y, x + width, y + height, PANEL_SOFT);
        guiGraphics.fill(x, y, x + 3, y + height, accent);
        drawBorder(guiGraphics, x, y, width, height, 0x403B4454);
        drawText(guiGraphics, label, x + 9, y + 6, width - 14, TEXT_DIM);
        drawText(guiGraphics, value, x + 9, y + 20, width - 14, valueColor);
    }

    private void drawNetworkPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, PANEL_SOFT);
        guiGraphics.fill(x, y, x + 3, y + height, BORDER);
        drawBorder(guiGraphics, x, y, width, height, 0x403B4454);
        String text = String.format("防御网  光凌:%d  电圈:%d  机枪:%d",
                menu.getNearbyPrismCount(), menu.getNearbyTeslaCount(), menu.getNearbyGatlingCount());
        drawText(guiGraphics, text, x + 9, y + 6, width - 14, TEXT);
    }

    private void drawText(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, int color) {
        guiGraphics.drawString(this.font, Component.literal(fitText(text, maxWidth)), x, y, color);
    }

    private String fitText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private String formatFe(int value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return Math.round(value / 1_000.0f) + "k";
        }
        return String.format("%,d", value);
    }

    private String formatTicks(int ticks) {
        int minutes = ticks / 1200;
        int seconds = (ticks % 1200) / 20;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String getStateText(int state) {
        return switch (state) {
            case 0 -> "离线";
            case 1 -> "待机";
            case 2 -> "防卫中";
            case 3 -> "崩溃";
            case 4 -> "警告";
            default -> "未知";
        };
    }

    private int getStateColor(int state, float partialTick) {
        long gameTime = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        float pulse = 0.65f + 0.35f * Mth.sin((gameTime + partialTick) * 0.16f);
        return switch (state) {
            case 0 -> 0xFF59606B;
            case 1 -> GREEN;
            case 2 -> pulseColor(0xFF5D73FF, pulse);
            case 3 -> RED;
            case 4 -> pulseColor(AMBER, pulse);
            default -> TEXT_DIM;
        };
    }

    private int getEnergyColor(int energy, int maxEnergy) {
        if (maxEnergy <= 0) return TEXT_DIM;
        float ratio = (float) energy / maxEnergy;
        if (ratio > 0.5f) return CYAN;
        if (ratio > 0.2f) return AMBER;
        return RED;
    }

    private int getStabilityColor(int stability, int maxStability) {
        if (maxStability <= 0) return TEXT_DIM;
        float ratio = (float) stability / maxStability;
        if (ratio > 0.5f) return GREEN;
        if (ratio > 0.2f) return AMBER;
        return RED;
    }

    private int getThreatColor(int threatLevel) {
        return switch (threatLevel) {
            case 0 -> TEXT_DIM;
            case 1, 2 -> CYAN;
            case 3 -> AMBER;
            default -> RED;
        };
    }

    private int pulseColor(int color, float pulse) {
        int r = (int) (((color >> 16) & 0xFF) * pulse);
        int g = (int) (((color >> 8) & 0xFF) * pulse);
        int b = (int) ((color & 0xFF) * pulse);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
