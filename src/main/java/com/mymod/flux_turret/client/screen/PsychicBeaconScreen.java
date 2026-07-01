package com.mymod.flux_turret.client.screen;

import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.client.renderer.PsychicBeaconRenderer;
import com.mymod.flux_turret.menu.PsychicBeaconMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class PsychicBeaconScreen extends AbstractContainerScreen<PsychicBeaconMenu> {
    private static final int PANEL = 0xF0101218;
    private static final int PANEL_SOFT = 0xE0181D26;
    private static final int HEADER = 0xFF1B202B;
    private static final int BORDER = 0xFF3FD6FF;
    private static final int BORDER_SOFT = 0x504A6F8F;
    private static final int TEXT = 0xFFE7E1EC;
    private static final int TEXT_DIM = 0xFFAFA7B8;
    private static final int CYAN = 0xFF31D7FF;
    private static final int MAGENTA = 0xFFFF57DF;
    private static final int GREEN = 0xFF72E68A;
    private static final int AMBER = 0xFFFFC45A;
    private static final int RED = 0xFFFF5B6B;

    private Button toggleButton;
    private Button doctrineButton;
    private Button linkButton;
    private final Button[] buffButtons = new Button[PsychicBeaconBlockEntity.BUFF_COUNT];

    public PsychicBeaconScreen(PsychicBeaconMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 252;
        this.imageHeight = 224;
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
                .bounds(x + this.imageWidth - 64, y + 8, 52, 18)
                .build());
        doctrineButton = addRenderableWidget(Button.builder(
                Component.literal(""),
                b -> menu.cycleDoctrine())
                .bounds(x + this.imageWidth - 126, y + 8, 58, 18)
                .build());
        linkButton = addRenderableWidget(Button.builder(
                Component.literal(""),
                b -> PsychicBeaconRenderer.toggleNetworkLinks())
                .bounds(x + this.imageWidth - 184, y + 8, 54, 18)
                .build());
        for (int i = 0; i < buffButtons.length; i++) {
            final int buffIndex = i;
            int col = i % 2;
            int row = i / 2;
            buffButtons[i] = addRenderableWidget(Button.builder(
                    Component.literal(""),
                    b -> menu.toggleBuff(buffIndex))
                    .bounds(x + 16 + col * 43, y + 139 + row * 18, 40, 16)
                    .build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x + 4, y + 5, x + this.imageWidth + 4, y + this.imageHeight + 5, 0x78000000);
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        guiGraphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 30, HEADER);
        guiGraphics.fill(x + 1, y + 30, x + this.imageWidth - 1, y + 31, 0x8031D7FF);
        guiGraphics.fill(x + 10, y + 38, x + 102, y + 194, 0x90101520);
        guiGraphics.fill(x + 112, y + 38, x + this.imageWidth - 10, y + 194, 0x90101520);
        guiGraphics.fill(x + 10, y + 201, x + this.imageWidth - 10, y + 213, 0xA0141822);
        drawBorder(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER);
        drawBorder(guiGraphics, x + 1, y + 1, this.imageWidth - 2, this.imageHeight - 2, BORDER_SOFT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (toggleButton != null) {
            boolean enabled = menu.getEnabled() == 1;
            toggleButton.setMessage(Component.translatable(enabled
                    ? "screen.flux_turret.psychic_beacon.stop"
                    : "screen.flux_turret.psychic_beacon.start"));
        }
        if (doctrineButton != null) {
            doctrineButton.setMessage(Component.translatable(PsychicBeaconBlockEntity.getDoctrineTranslationKey(menu.getDoctrine())));
        }
        if (linkButton != null) {
            linkButton.setMessage(Component.translatable(PsychicBeaconRenderer.shouldRenderNetworkLinks()
                    ? "screen.flux_turret.psychic_beacon.links_on"
                    : "screen.flux_turret.psychic_beacon.links_off"));
        }
        updateBuffButtons();

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int state = menu.getBeaconState();
        int stateColor = getStateColor(state, partialTick);

        guiGraphics.fill(x + 10, y + 10, x + 18, y + 18, 0xFF2C3038);
        guiGraphics.fill(x + 11, y + 11, x + 17, y + 17, stateColor);
        drawText(guiGraphics, Component.translatable("container.flux_turret.psychic_beacon").getString(),
                x + 24, y + 9, 150, 0xFFEAF8FF);

        int energy = menu.getEnergyStored();
        int maxEnergy = menu.getMaxEnergy();
        int drainRate = TurretConfig.PSYCHIC_BEACON_DRAIN_RATE.get();
        int stability = menu.getStability();
        int stabilityMax = TurretConfig.PSYCHIC_BEACON_STABILITY.get();
        int threatLevel = menu.getThreatLevel();
        int kills = menu.getTodayKills();
        int minKills = PsychicBeaconBlockEntity.getRequiredKillsForThreatLevel(threatLevel);
        int dawnCost = TurretConfig.PSYCHIC_BEACON_DAWN_COST.get();

        drawBeaconDiagram(guiGraphics, x + 20, y + 47, stateColor, threatLevel, partialTick);
        drawText(guiGraphics, tr("screen.flux_turret.psychic_beacon.buffs"), x + 16, y + 125, 78, TEXT_DIM);
        drawText(guiGraphics, Component.translatable("screen.flux_turret.psychic_beacon.level_select",
                        threatLevel, PsychicBeaconBlockEntity.getMaxSelectedBuffs(threatLevel)).getString(),
                x + 19, y + 191, 74, getThreatColor(threatLevel));

        drawText(guiGraphics, tr("screen.flux_turret.psychic_beacon.energy"), x + 122, y + 43, 76, TEXT_DIM);
        renderEnergyBar(guiGraphics, x + 122, y + 57, 110, 12);
        drawText(guiGraphics, formatFe(energy) + " / " + formatFe(maxEnergy) + " FE", x + 122, y + 73, 110, getEnergyColor(energy, maxEnergy));
        drawText(guiGraphics, Component.translatable("screen.flux_turret.psychic_beacon.drain", formatFe(drainRate)).getString(),
                x + 122, y + 84, 110, TEXT_DIM);

        drawProgressMetric(guiGraphics, x + 122, y + 102, 110, tr("screen.flux_turret.psychic_beacon.stability"),
                stability + " / " + stabilityMax, stability, stabilityMax, getStabilityColor(stability, stabilityMax));
        drawProgressMetric(guiGraphics, x + 122, y + 132, 110, tr("screen.flux_turret.psychic_beacon.kills"),
                kills + " / " + minKills, kills, minKills, kills >= minKills ? GREEN : AMBER);
        drawMetric(guiGraphics, x + 122, y + 162, 52, 28,
                tr("screen.flux_turret.psychic_beacon.dawn"), formatTicks(menu.getTimeUntilDawn()), CYAN, TEXT);
        drawMetric(guiGraphics, x + 180, y + 162, 52, 28,
                tr("screen.flux_turret.psychic_beacon.cost"), formatFe(dawnCost), energy >= dawnCost ? GREEN : AMBER, energy >= dawnCost ? GREEN : AMBER);

        drawNetworkPanel(guiGraphics, x + 10, y + 200, this.imageWidth - 20, 14);
    }

    private void updateBuffButtons() {
        int threatLevel = menu.getThreatLevel();
        int selectedMask = menu.getSelectedBuffMask();
        int maxSelected = PsychicBeaconBlockEntity.getMaxSelectedBuffs(threatLevel);
        boolean selectionFull = Integer.bitCount(selectedMask & PsychicBeaconBlockEntity.getUnlockedBuffMask(threatLevel)) >= maxSelected;

        for (int i = 0; i < buffButtons.length; i++) {
            Button button = buffButtons[i];
            if (button == null) continue;
            boolean unlocked = PsychicBeaconBlockEntity.isBuffUnlocked(i, threatLevel);
            boolean selected = (selectedMask & (1 << i)) != 0;
            button.active = unlocked && (selected || !selectionFull);
            button.setMessage(Component.literal((selected ? ">" : "") + getBuffName(i)));
        }
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
        guiGraphics.fill(x, y, x + 2, y + height, accent);
        drawBorder(guiGraphics, x, y, width, height, 0x403B4454);
        drawText(guiGraphics, label, x + 7, y + 4, width - 10, TEXT_DIM);
        drawText(guiGraphics, value, x + 7, y + 17, width - 10, valueColor);
    }

    private void drawProgressMetric(GuiGraphics guiGraphics, int x, int y, int width,
            String label, String value, int current, int max, int color) {
        guiGraphics.fill(x, y, x + width, y + 24, PANEL_SOFT);
        drawBorder(guiGraphics, x, y, width, 24, 0x403B4454);
        drawText(guiGraphics, label, x + 7, y + 4, 46, TEXT_DIM);
        drawText(guiGraphics, value, x + 52, y + 4, width - 58, color);
        float ratio = max > 0 ? Mth.clamp((float) current / max, 0.0f, 1.0f) : 0.0f;
        int fill = Math.round((width - 14) * ratio);
        guiGraphics.fill(x + 7, y + 16, x + width - 7, y + 19, 0xFF10141C);
        guiGraphics.fill(x + 7, y + 16, x + 7 + fill, y + 19, color);
    }

    private void drawBeaconDiagram(GuiGraphics guiGraphics, int x, int y, int stateColor, int threatLevel, float partialTick) {
        long gameTime = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        float pulse = 0.65f + 0.35f * Mth.sin((gameTime + partialTick) * 0.18f);
        int core = pulseColor(stateColor, pulse);

        guiGraphics.fill(x + 16, y + 76, x + 56, y + 84, 0xFF252A33);
        guiGraphics.fill(x + 22, y + 66, x + 50, y + 75, 0xFF4A344E);
        guiGraphics.fill(x + 34, y + 30, x + 38, y + 66, 0xFF2D2438);
        guiGraphics.fill(x + 16, y + 45, x + 56, y + 47, 0xFF8E602A);
        guiGraphics.fill(x + 21, y + 53, x + 51, y + 55, 0xFF8E602A);
        guiGraphics.fill(x + 12, y + 25, x + 16, y + 56, 0xFF9EA4AD);
        guiGraphics.fill(x + 56, y + 25, x + 60, y + 56, 0xFF9EA4AD);
        guiGraphics.fill(x + 19, y + 21, x + 53, y + 23, 0xFF8E602A);
        guiGraphics.fill(x + 19, y + 58, x + 53, y + 60, 0xFF8E602A);
        guiGraphics.fill(x + 30, y + 8, x + 42, y + 20, core);
        guiGraphics.fill(x + 28, y + 11, x + 44, y + 17, core);
        guiGraphics.fill(x + 35, y, x + 37, y + 88, 0x5531D7FF);
        if (threatLevel >= 3) {
            guiGraphics.fill(x + 33, y, x + 39, y + 88, 0x33FF57DF);
        }
    }

    private String getBuffName(int index) {
        return Component.translatable(switch (index) {
            case PsychicBeaconBlockEntity.BUFF_SPEED -> "buff.flux_turret.speed";
            case PsychicBeaconBlockEntity.BUFF_HASTE -> "buff.flux_turret.haste";
            case PsychicBeaconBlockEntity.BUFF_RESISTANCE -> "buff.flux_turret.resistance";
            case PsychicBeaconBlockEntity.BUFF_STRENGTH -> "buff.flux_turret.strength";
            case PsychicBeaconBlockEntity.BUFF_REGENERATION -> "buff.flux_turret.regeneration";
            default -> "buff.flux_turret.unknown";
        }).getString();
    }

    private void drawNetworkPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0x00141822);
        String text = Component.translatable("screen.flux_turret.psychic_beacon.network",
                menu.getNearbyPrismCount(), menu.getNearbyTeslaCount(), menu.getNearbyGatlingCount(),
                Component.translatable(PsychicBeaconBlockEntity.getAffixTranslationKey(menu.getActiveAffix())).getString(),
                menu.getLastBattleScore()).getString();
        drawText(guiGraphics, text, x + 7, y + 3, width - 14, TEXT);
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
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
            case 0 -> tr("screen.flux_turret.psychic_beacon.state.offline");
            case 1 -> tr("screen.flux_turret.psychic_beacon.state.idle");
            case 2 -> tr("screen.flux_turret.psychic_beacon.state.active");
            case 3 -> tr("screen.flux_turret.psychic_beacon.state.failed");
            case 4 -> tr("screen.flux_turret.psychic_beacon.state.warning");
            default -> tr("screen.flux_turret.psychic_beacon.state.unknown");
        };
    }

    private int getStateColor(int state, float partialTick) {
        long gameTime = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        float pulse = 0.65f + 0.35f * Mth.sin((gameTime + partialTick) * 0.16f);
        return switch (state) {
            case 0 -> 0xFF59606B;
            case 1 -> GREEN;
            case 2 -> pulseColor(MAGENTA, pulse);
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
