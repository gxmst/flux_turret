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
    private Button toggleButton;

    public PsychicBeaconScreen(PsychicBeaconMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 195;
        this.imageHeight = 192;
        this.inventoryLabelY = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        toggleButton = addRenderableWidget(Button.builder(
                Component.literal(""),
                b -> {
                    menu.toggleEnabled();
                })
                .bounds(x + this.imageWidth - 70, y + 5, 60, 14)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xD0181818);
        drawBorder(guiGraphics, x, y, 0xFF9944BB, 1);
        drawBorder(guiGraphics, x + 1, y + 1, 0x409944BB, 1);

        renderEnergyBar(guiGraphics, x, y, partialTick);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int color, int thickness) {
        g.fill(x, y, x + this.imageWidth, y + thickness, color);
        g.fill(x, y + this.imageHeight - thickness, x + this.imageWidth, y + this.imageHeight, color);
        g.fill(x, y, x + thickness, y + this.imageHeight, color);
        g.fill(x + this.imageWidth - thickness, y, x + this.imageWidth, y + this.imageHeight, color);
    }

    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y, float partialTick) {
        int energy = menu.getEnergyStored();
        int maxEnergy = menu.getMaxEnergy();
        float ratio = maxEnergy > 0 ? (float) energy / maxEnergy : 0;

        int barX = x + 10;
        int barY = y + 24;
        int barWidth = this.imageWidth - 20;
        int barHeight = 10;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);

        int filledWidth = (int) (barWidth * ratio);
        if (filledWidth > 0) {
            int baseColor;
            if (ratio > 0.5f) {
                baseColor = 0xFF00CCFF;
            } else if (ratio > 0.2f) {
                baseColor = 0xFFFFAA00;
            } else {
                baseColor = 0xFFFF3333;
            }
            float gameTime = this.minecraft.level.getGameTime() + partialTick;
            float pulse = 0.85f + 0.15f * Mth.sin(gameTime * 0.05f);
            int r = (int) (((baseColor >> 16) & 0xFF) * pulse);
            int g = (int) (((baseColor >> 8) & 0xFF) * pulse);
            int b = (int) ((baseColor & 0xFF) * pulse);
            int pulsedColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, pulsedColor);

            int highlightEnd = Math.min(filledWidth, 3);
            guiGraphics.fill(barX, barY, barX + highlightEnd, barY + barHeight / 2, 0x40FFFFFF);
        }

        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFF505050);
        guiGraphics.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFF505050);
        guiGraphics.fill(barX - 1, barY, barX, barY + barHeight, 0xFF505050);
        guiGraphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, 0xFF505050);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int textX = x + 12;
        int textY = y + 6;

        int state = menu.getBeaconState();
        int indicatorColor = getStateIndicatorColor(state, partialTick);
        guiGraphics.fill(x + 8, y + 8, x + 16, y + 16, 0xFF404040);
        guiGraphics.fill(x + 9, y + 9, x + 15, y + 15, indicatorColor);

        guiGraphics.drawString(this.font,
                Component.translatable("container.flux_turret.psychic_beacon"),
                x + 20, textY, 0xFFCC88EE);
        textY += 16;

        boolean enabled = menu.getEnabled() == 1;
        if (toggleButton != null) {
            toggleButton.setMessage(Component.literal(enabled ? "\u00A7a\u25CF ON" : "\u00A7c\u25CF OFF"));
        }

        int energy = menu.getEnergyStored();
        int maxEnergy = menu.getMaxEnergy();
        int drainRate = TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get();
        guiGraphics.drawString(this.font,
                Component.literal(String.format("\u26A1 %d / %d FE  (%d FE/t)", energy, maxEnergy, drainRate)),
                textX, textY, 0xFF00CCFF);
        textY += 18;

        String stateStr = switch (state) {
            case 0 -> "\u00A78\u79BB\u7EBF";
            case 1 -> "\u00A7a\u5F85\u673A\u4E2D";
            case 2 -> "\u00A7c\u6218\u6597\u9632\u536B\u4E2D";
            case 3 -> "\u00A74\u5D29\u6E83";
            case 4 -> "\u00A7e\u8D85\u8F7D\u8B66\u544A";
            default -> "\u00A77\u672A\u77E5";
        };
        guiGraphics.drawString(this.font,
                Component.literal("\u72B6\u6001: " + stateStr),
                textX, textY, 0xFFE0E0E0);
        textY += 14;

        int threatLevel = menu.getThreatLevel();
        int radius = (threatLevel + 1) * 10;
        guiGraphics.drawString(this.font,
                Component.literal(String.format("\u5E95\u5EA7\u5C42\u7EA7: Lv.%d  (\u5E7F\u64AD\u534A\u5F84: %d\u683C)", threatLevel, radius)),
                textX, textY, 0xFFE0E0E0);
        textY += 16;

        guiGraphics.drawString(this.font,
                Component.literal("\u00A7b[\u96F7\u8FBE\u9632\u5FA1\u7F51\u626B\u63CF]"),
                textX, textY, 0xFF9944BB);
        textY += 12;

        int prismCount = menu.getNearbyPrismCount();
        int teslaCount = menu.getNearbyTeslaCount();
        int gatlingCount = menu.getNearbyGatlingCount();
        guiGraphics.drawString(this.font,
                Component.literal(String.format("  \u25B6 \u5149\u51CC\u5854: %d | \u7279\u65AF\u62C9: %d | \u52A0\u7279\u6797: %d", prismCount, teslaCount, gatlingCount)),
                textX, textY, 0xFFC0C0C0);
        textY += 16;

        int kills = menu.getTodayKills();
        int minKills = TurretConfig.PSYCHIC_BEACON_MIN_KILLS.get();
        int killColor = kills >= minKills ? 0xFF55FF55 : 0xFFFF5555;
        guiGraphics.drawString(this.font,
                Component.literal(String.format("\u2605 \u4ECA\u65E5\u51C0\u5316: %d \u53EA  (\u95E8\u69DB: %d\u53EA)", kills, minKills)),
                textX, textY, killColor);
        textY += 14;

        int stability = menu.getStability();
        int stabColor = stability > 50 ? 0xFF55FF55 : stability > 20 ? 0xFFFFAA00 : 0xFFFF3333;
        guiGraphics.drawString(this.font,
                Component.literal(String.format("\u7A33\u5B9A\u5EA6: %d / 100", stability)),
                textX, textY, stabColor);
        textY += 16;

        int ticksUntilDawn = menu.getTimeUntilDawn();
        int minutes = ticksUntilDawn / 1200;
        int seconds = (ticksUntilDawn % 1200) / 20;
        guiGraphics.drawString(this.font,
                Component.literal(String.format("\u23F1 \u8DDD\u6E05\u6668\u5408\u6210: %02d:%02d", minutes, seconds)),
                textX, textY, 0xFFE0E0E0);
        textY += 14;

        int dawnCost = TurretConfig.PSYCHIC_BEACON_DAWN_COST.get();
        boolean canAfford = energy >= dawnCost;
        guiGraphics.drawString(this.font,
                Component.literal(String.format("  \u5408\u6210\u80FD\u8017: %d FE %s", dawnCost, canAfford ? "\u00A7a[\u5145\u8DB3]" : "\u00A7c[\u4E0D\u8DB3]")),
                textX, textY, 0xFFC0C0C0);
    }

    private int getStateIndicatorColor(int state, float partialTick) {
        float gameTime = this.minecraft.level.getGameTime() + partialTick;
        float pulse = 0.7f + 0.3f * Mth.sin(gameTime * 0.1f);
        return switch (state) {
            case 0 -> 0xFF444444;
            case 1 -> 0xFF44FF44;
            case 2 -> {
                float p = pulse;
                int r2 = (int) (0x44 * p);
                int g2 = (int) (0x44 * p);
                int b2 = (int) (0xFF * p);
                yield (0xFF << 24) | (r2 << 16) | (g2 << 8) | b2;
            }
            case 3 -> 0xFFFF2222;
            case 4 -> {
                float p = 0.5f + 0.5f * Mth.sin(gameTime * 0.25f);
                yield 0xFF000000 | ((int) (0xFF * p) << 16) | ((int) (0x88 * p) << 8);
            }
            default -> 0xFF888888;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
