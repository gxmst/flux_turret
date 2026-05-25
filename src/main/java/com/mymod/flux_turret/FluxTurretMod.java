package com.mymod.flux_turret;

import com.mymod.flux_turret.client.TurretConfigScreen;
import com.mymod.flux_turret.client.renderer.PrismTowerRenderer;
import com.mymod.flux_turret.client.renderer.GatlingTurretRenderer;
import com.mymod.flux_turret.client.renderer.GrandCannonRenderer;
import com.mymod.flux_turret.client.renderer.TeslaCoilRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod(FluxTurretMod.MOD_ID)
public class FluxTurretMod {
    public static final String MOD_ID = "flux_turret";
    private static final Logger LOGGER = LogManager.getLogger();

    public FluxTurretMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TurretConfig.SPEC);

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new TurretConfigScreen(parent)));

        ModRegistry.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        GeckoLib.initialize();

        LOGGER.info("gxFlux Mod Initialized");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModRegistry.PRISM_TOWER_BE.get(), PrismTowerRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.TESLA_COIL_BE.get(), TeslaCoilRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.GATLING_TURRET_BE.get(), GatlingTurretRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.GRAND_CANNON_BE.get(), GrandCannonRenderer::new);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            if (event.getItemStack().getItem() instanceof BlockItem blockItem) {
                String blockId = blockItem.getBlock().getDescriptionId()
                        .replace("block.", "tooltip.");
                Component tooltip = Component.translatable(blockId);
                if (tooltip.getString() != null && !tooltip.getString().equals(blockId)) {
                    event.getToolTip().add(tooltip);
                }
            }
        }
    }
}
