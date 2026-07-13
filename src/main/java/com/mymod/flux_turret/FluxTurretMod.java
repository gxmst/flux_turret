package com.mymod.flux_turret;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
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
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod(FluxTurretMod.MOD_ID)
public class FluxTurretMod {
    public static final String MOD_ID = "flux_turret";
    private static final Logger LOGGER = LogManager.getLogger();

    public FluxTurretMod(FMLJavaModLoadingContext loadingContext) {
        IEventBus modEventBus = loadingContext.getModEventBus();

        // All values affect server-authoritative gameplay. SERVER configs are
        // synchronized to joining clients; COMMON configs are not.
        loadingContext.registerConfig(ModConfig.Type.SERVER, TurretConfig.SPEC);

        ModRegistry.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        ModNetworking.init();

        GeckoLib.initialize();

        LOGGER.info("gxFlux Mod Initialized - Version {}", TurretConfig.SPEC.isLoaded() ? "Loaded" : "Loading");
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
                String blockId = event.getItemStack().getDescriptionId()
                        .replace("block.", "tooltip.")
                        .replace("item.", "tooltip.");
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
        public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            // Static beacon registry must not outlive the server (integrated server
            // re-entry, dimension churn) — drop all tracked references.
            PsychicBeaconBlockEntity.clearActiveBeacons();
            PrismTowerBlockEntity.clearLoadedTowers();
            // Same for the per-level shared monster scan cache; its keys are
            // ServerLevels that must not be pinned past server shutdown.
            com.mymod.flux_turret.util.TurretScanCache.clearAll();
        }

        @SubscribeEvent
        public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
            // A level going away (dimension unload, server swap) should not leave
            // dangling beacon references in the static registry. setRemoved() covers
            // normal block-entity teardown, but a bulk level unload is not guaranteed
            // to fire it for every tracked beacon, so prune by level explicitly.
            if (event.getLevel() instanceof net.minecraft.world.level.Level level && !level.isClientSide) {
                PsychicBeaconBlockEntity.clearBeaconsForLevel(level);
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    PrismTowerBlockEntity.clearTowersForLevel(serverLevel);
                    com.mymod.flux_turret.util.TurretScanCache.clearLevel(serverLevel);
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerSleep(PlayerSleepInBedEvent event) {
            Player player = event.getEntity();
            Level level = player.level();
            if (level.isClientSide) return;
            if (PsychicBeaconBlockEntity.hasActiveBattle(level)) {
                event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
                player.displayClientMessage(
                    Component.translatable("message.flux_turret.psychic_wave")
                        .withStyle(net.minecraft.ChatFormatting.RED),
                    true
                );
            }
        }

        @SubscribeEvent
        public static void onMobDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
                Level level = mob.level();
                if (level.isClientSide) return;

                BlockPos spawnedByBeaconPos = PsychicBeaconBlockEntity.getBeaconSpawnPos(mob);
                if (spawnedByBeaconPos != null) {
                    BlockEntity blockEntity = level.getBlockEntity(spawnedByBeaconPos);
                    if (blockEntity instanceof PsychicBeaconBlockEntity beacon
                            && beacon.getBeaconState() == PsychicBeaconBlockEntity.STATE_ACTIVE) {
                        beacon.incrementTodayKills();
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide || !(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) {
                return;
            }
            BlockPos beaconPos = PsychicBeaconBlockEntity.getBeaconSpawnPos(mob);
            if (beaconPos != null) {
                PsychicBeaconBlockEntity.ensureMoveToBeaconGoal(mob, beaconPos);
            }
        }
    }
}
