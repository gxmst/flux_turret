package com.mymod.flux_turret;

import com.mymod.flux_turret.client.renderer.PrismTowerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

        // Register the deferred registers to the event bus
        ModRegistry.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Initialize GeckoLib
        GeckoLib.initialize();

        LOGGER.info("Flux Turret Mod Initialized");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModRegistry.PRISM_TOWER_BE.get(), PrismTowerRenderer::new);
        }
    }
}
