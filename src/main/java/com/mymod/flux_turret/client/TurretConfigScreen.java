package com.mymod.flux_turret.client;

import com.mymod.flux_turret.TurretConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TurretConfigScreen extends Screen {
    private final Screen parent;

    public TurretConfigScreen(Screen parent) {
        super(Component.translatable("flux_turret.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = 36;
        int lineH = 22;

        // Gatling
        addRenderableWidget(label(centerX, y, "Gatling Range: " + TurretConfig.GATLING_RANGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Gatling Damage: " + TurretConfig.GATLING_DAMAGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Gatling Fire Cost: " + TurretConfig.GATLING_FIRE_COST.get())); y += lineH + 6;

        // Tesla
        addRenderableWidget(label(centerX, y, "Tesla Range: " + TurretConfig.TESLA_RANGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Tesla Damage: " + TurretConfig.TESLA_DAMAGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Tesla Fire Cost: " + TurretConfig.TESLA_FIRE_COST.get())); y += lineH + 6;

        // Prism
        addRenderableWidget(label(centerX, y, "Prism Range: " + TurretConfig.PRISM_RANGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Prism Damage: " + TurretConfig.PRISM_DAMAGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Prism Master/Slave Cost: " + TurretConfig.PRISM_MASTER_FIRE_COST.get() + "/" + TurretConfig.PRISM_SLAVE_FIRE_COST.get())); y += lineH + 6;

        // Grand Cannon
        addRenderableWidget(label(centerX, y, "Grand Cannon Range: " + TurretConfig.GRAND_CANNON_RANGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Grand Cannon Damage: " + TurretConfig.GRAND_CANNON_DAMAGE.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Grand Cannon Explosion Radius: " + TurretConfig.GRAND_CANNON_EXPLOSION_RADIUS.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Grand Cannon Fire Cost: " + TurretConfig.GRAND_CANNON_FIRE_COST.get())); y += lineH;
        addRenderableWidget(label(centerX, y, "Grand Cannon Cooldown: " + TurretConfig.GRAND_CANNON_COOLDOWN.get() + " ticks")); y += lineH + 6;

        // General
        addRenderableWidget(Button.builder(
                Component.literal("Friendly Fire Protection: " + TurretConfig.FRIENDLY_FIRE_PROTECTION.get()),
                b -> {
                    TurretConfig.FRIENDLY_FIRE_PROTECTION.set(!TurretConfig.FRIENDLY_FIRE_PROTECTION.get());
                    TurretConfig.SPEC.save();
                    b.setMessage(Component.literal("Friendly Fire Protection: " + TurretConfig.FRIENDLY_FIRE_PROTECTION.get()));
                }).bounds(centerX - 100, y, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> this.minecraft.setScreen(this.parent)).bounds(centerX - 50, this.height - 28, 100, 20).build());
    }

    private Button label(int centerX, int y, String text) {
        return Button.builder(Component.literal(text), b -> {}).bounds(centerX - 100, y, 200, 20).build();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal("Edit values in flux_turret-common.toml"), this.width / 2, this.height - 50, 0xAAAAAA);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
