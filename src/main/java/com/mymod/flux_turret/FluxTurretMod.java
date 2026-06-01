package com.mymod.flux_turret;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.client.TurretConfigScreen;
import com.mymod.flux_turret.client.renderer.EnergyCrystalRenderer;
import com.mymod.flux_turret.client.renderer.GatlingTurretRenderer;
import com.mymod.flux_turret.client.renderer.GrandCannonRenderer;
import com.mymod.flux_turret.client.renderer.PrismTowerRenderer;
import com.mymod.flux_turret.client.renderer.PsychicBeaconRenderer;
import com.mymod.flux_turret.client.renderer.TeslaCoilRenderer;
import com.mymod.flux_turret.client.screen.PsychicBeaconScreen;
import com.mymod.flux_turret.network.ModNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
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

        ModNetworking.init();

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
            event.registerBlockEntityRenderer(ModRegistry.ENERGY_CRYSTAL_BE.get(), EnergyCrystalRenderer::new);
            event.registerBlockEntityRenderer(ModRegistry.PSYCHIC_BEACON_BE.get(), PsychicBeaconRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(ModRegistry.PSYCHIC_BEACON_MENU.get(), PsychicBeaconScreen::new);
            });
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

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onPlayerSleep(PlayerSleepInBedEvent event) {
            Player player = event.getEntity();
            Level level = player.level();
            if (level.isClientSide) return;
            BlockPos playerPos = player.blockPosition();
            if (findNearbyActiveBeacon(level, playerPos, 100) != null) {
                event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
                player.displayClientMessage(Component.literal("\u00a7c\u5f3a\u70c8\u7684\u5fc3\u7075\u6ce2\u5728\u7a7a\u6c14\u4e2d\u6fc0\u8361\u2026\u2026\u4f60\u7684\u5927\u8111\u6781\u5ea6\u4ea2\u594b\uff0c\u65e0\u6cd5\u5165\u7761\uff01"), true);
            }
        }

        @SubscribeEvent
        public static void onMobDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster monster) {
                Level level = monster.level();
                if (level.isClientSide) return;

                BlockPos deathPos = monster.blockPosition();
                PsychicBeaconBlockEntity beacon = findNearbyActiveBeacon(level, deathPos, 32);
                if (beacon != null) {
                    beacon.incrementTodayKills();
                }
            }
        }

        @Nullable
        private static PsychicBeaconBlockEntity findNearbyActiveBeacon(Level level, BlockPos pos, int range) {
            int minX = pos.getX() - range;
            int minZ = pos.getZ() - range;
            int maxX = pos.getX() + range;
            int maxZ = pos.getZ() + range;
            for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
                for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) {
                    if (level.hasChunk(cx, cz)) {
                        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (be instanceof PsychicBeaconBlockEntity beacon
                                    && beacon.getBeaconState() == PsychicBeaconBlockEntity.STATE_ACTIVE) {
                                if (beacon.getBlockPos().distManhattan(pos) <= range) {
                                    return beacon;
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }
    }
}
