package com.mymod.flux_turret;

import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.block.entity.ProtectedRewardChestBlockEntity;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.client.renderer.EnergyCrystalRenderer;
import com.mymod.flux_turret.client.renderer.GatlingTurretRenderer;
import com.mymod.flux_turret.client.renderer.GrandCannonRenderer;
import com.mymod.flux_turret.client.renderer.PrismTowerRenderer;
import com.mymod.flux_turret.client.renderer.PsychicBeaconRenderer;
import com.mymod.flux_turret.client.renderer.TeslaCoilRenderer;
import com.mymod.flux_turret.client.screen.PsychicBeaconScreen;
import com.mymod.flux_turret.client.screen.TurretInspectorScreen;
import com.mymod.flux_turret.client.TurretClientConfig;
import com.mymod.flux_turret.network.ModNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.ChatFormatting;
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
        loadingContext.registerConfig(ModConfig.Type.CLIENT, TurretClientConfig.SPEC);

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
                MenuScreens.register(ModRegistry.TURRET_INSPECTOR_MENU.get(), TurretInspectorScreen::new);
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
                if (isTurretItem(event.getItemStack())) {
                    if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                        appendTurretStats(event);
                    } else {
                        event.getToolTip().add(Component.translatable("tooltip.flux_turret.hold_shift")
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
        }

        private static boolean isTurretItem(net.minecraft.world.item.ItemStack stack) {
            return stack.is(ModRegistry.GATLING_TURRET_ITEM.get())
                    || stack.is(ModRegistry.TESLA_COIL_ITEM.get())
                    || stack.is(ModRegistry.PRISM_TOWER_ITEM.get())
                    || stack.is(ModRegistry.GRAND_CANNON_ITEM.get());
        }

        private static void appendTurretStats(ItemTooltipEvent event) {
            net.minecraft.world.item.ItemStack stack = event.getItemStack();
            double minRange = 0.0D;
            double range;
            double damage;
            int cost;
            int capacity;
            String detailKey;
            if (stack.is(ModRegistry.GATLING_TURRET_ITEM.get())) {
                range = TurretConfig.GATLING_RANGE.get();
                damage = TurretConfig.GATLING_DAMAGE.get();
                cost = TurretConfig.GATLING_FIRE_COST.get();
                capacity = TurretConfig.GATLING_CAPACITY.get();
                detailKey = "tooltip.flux_turret.gatling_detail";
            } else if (stack.is(ModRegistry.TESLA_COIL_ITEM.get())) {
                range = TurretConfig.TESLA_RANGE.get();
                damage = TurretConfig.TESLA_DAMAGE.get();
                cost = TurretConfig.TESLA_FIRE_COST.get();
                capacity = TurretConfig.TESLA_CAPACITY.get();
                detailKey = "tooltip.flux_turret.tesla_detail";
            } else if (stack.is(ModRegistry.PRISM_TOWER_ITEM.get())) {
                range = TurretConfig.PRISM_RANGE.get();
                damage = TurretConfig.PRISM_DAMAGE.get();
                cost = TurretConfig.PRISM_MASTER_FIRE_COST.get();
                capacity = TurretConfig.PRISM_CAPACITY.get();
                detailKey = "tooltip.flux_turret.prism_detail";
            } else {
                minRange = TurretConfig.GRAND_CANNON_MIN_RANGE.get();
                range = TurretConfig.GRAND_CANNON_RANGE.get();
                damage = TurretConfig.GRAND_CANNON_DAMAGE.get();
                cost = TurretConfig.GRAND_CANNON_FIRE_COST.get();
                capacity = TurretConfig.GRAND_CANNON_CAPACITY.get();
                detailKey = "tooltip.flux_turret.cannon_detail";
            }
            Component rangeText = minRange > 0.0D
                    ? Component.translatable("tooltip.flux_turret.range_blind", minRange, range)
                    : Component.translatable("tooltip.flux_turret.range", range);
            event.getToolTip().add(Component.translatable("tooltip.flux_turret.turret_stats",
                    rangeText, damage, cost, capacity).withStyle(ChatFormatting.GRAY));
            net.minecraft.nbt.CompoundTag carriedData = stack.getTagElement("BlockEntityTag");
            if (carriedData != null && carriedData.contains("Energy", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
                event.getToolTip().add(Component.translatable("tooltip.flux_turret.turret_stored_energy",
                        carriedData.getInt("Energy"), capacity).withStyle(ChatFormatting.AQUA));
            }
            event.getToolTip().add(Component.translatable(detailKey).withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable("tooltip.flux_turret.inspector_open")
                    .withStyle(ChatFormatting.DARK_AQUA));
            event.getToolTip().add(Component.translatable("tooltip.flux_turret.redstone_modes")
                    .withStyle(ChatFormatting.DARK_GRAY));
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
            int configuredRadius = TurretConfig.PSYCHIC_BEACON_SLEEP_BLOCK_RADIUS.get();
            PsychicBeaconBlockEntity blockingBeacon = PsychicBeaconBlockEntity.findNearbyActiveBeacon(
                    level, player.blockPosition(), configuredRadius == 0 ? 30_000_000 : configuredRadius);
            if (blockingBeacon != null) {
                event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
                player.displayClientMessage(
                    Component.translatable("message.flux_turret.psychic_wave_source",
                            blockingBeacon.getOwnerName().isBlank()
                                    ? Component.translatable("screen.flux_turret.inspector.unowned")
                                    : blockingBeacon.getOwnerName(),
                            blockingBeacon.getBlockPos().getX(), blockingBeacon.getBlockPos().getY(),
                            blockingBeacon.getBlockPos().getZ())
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
        public static void onChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
            if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                    || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) {
                return;
            }
            // Snapshot only marked, still-protected chests before replacing map
            // entries. Direct chunk replacement closes the pre-first-tick hopper
            // window without triggering any additional chunk lookup.
            java.util.List<net.minecraft.world.level.block.entity.ChestBlockEntity> rewards =
                    chunk.getBlockEntities().values().stream()
                            .filter(net.minecraft.world.level.block.entity.ChestBlockEntity.class::isInstance)
                            .map(net.minecraft.world.level.block.entity.ChestBlockEntity.class::cast)
                            .filter(chest -> PsychicBeaconBlockEntity
                                    .isRewardChestProtectionActive(level, chest))
                            .toList();
            for (net.minecraft.world.level.block.entity.ChestBlockEntity reward : rewards) {
                ProtectedRewardChestBlockEntity.restoreProtectionWrapper(level, chunk, reward);
            }
        }

        @SubscribeEvent
        public static void onTurretBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
            if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return;
            Level level = player.level();
            BlockPos blockPos = event.getPos();
            net.minecraft.world.level.block.state.BlockState state = event.getState();

            if (state.getBlock() instanceof com.mymod.flux_turret.block.TurretExtensionBlock extension) {
                blockPos = extension.getCorePos(state, blockPos);
            }

            if (state.getBlock() instanceof com.mymod.flux_turret.block.GrandCannonBlock
                    && state.hasProperty(com.mymod.flux_turret.block.GrandCannonBlock.PART)
                    && state.hasProperty(com.mymod.flux_turret.block.GrandCannonBlock.FACING)) {
                com.mymod.flux_turret.block.GrandCannonBlock.CannonPart part =
                        state.getValue(com.mymod.flux_turret.block.GrandCannonBlock.PART);
                if (part != com.mymod.flux_turret.block.GrandCannonBlock.CannonPart.BACK_LEFT) {
                    blockPos = part.getCorePos(blockPos,
                            state.getValue(com.mymod.flux_turret.block.GrandCannonBlock.FACING));
                }
            }

            if (level.getBlockEntity(blockPos) instanceof com.mymod.flux_turret.block.entity.TurretBlockEntityBase turret
                    && !turret.canPlayerConfigure(player)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable(
                        "message.flux_turret.turret_access_denied"), true);
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

        @SubscribeEvent
        public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
            Player player = event.getPlayer();
            BlockPos pos = event.getPos();
            net.minecraft.world.level.block.state.BlockState state = event.getState();
            if (state.is(ModRegistry.PSYCHIC_BEACON_BLOCK.get())) {
                BlockPos beaconPos = state.getValue(com.mymod.flux_turret.block.PsychicBeaconBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                        ? pos.below() : pos;
                if (level.getBlockEntity(beaconPos) instanceof PsychicBeaconBlockEntity beacon
                        && !beacon.prepareForPlayerRemoval(player)) {
                    event.setCanceled(true);
                }
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!PsychicBeaconBlockEntity.canPlayerAccessRewardChest(player, blockEntity)) {
                event.setCanceled(true);
                player.displayClientMessage(
                        Component.translatable("message.flux_turret.beacon_reward_protected")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW),
                        true);
            }
        }

        @SubscribeEvent
        public static void onRightClickRewardChest(
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            Level level = player.level();
            if (level.isClientSide) return;
            BlockEntity blockEntity = level.getBlockEntity(event.getPos());
            if (!PsychicBeaconBlockEntity.canPlayerAccessRewardChest(player, blockEntity)) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
                player.displayClientMessage(
                        Component.translatable("message.flux_turret.beacon_reward_protected")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW),
                        true);
            }
        }

        @SubscribeEvent
        public static void onChestPlacedNearProtectedReward(
                net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide
                    || !(event.getPlacedBlock().getBlock()
                            instanceof net.minecraft.world.level.block.ChestBlock)
                    || !PsychicBeaconBlockEntity.hasAdjacentProtectedRewardChest(
                            level, event.getPos())) {
                return;
            }
            // Never allow a normal chest to become the second half of a protected
            // reward inventory. This also closes the untagged-half automation path.
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("message.flux_turret.beacon_reward_protected")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW),
                        true);
            }
        }

        @SubscribeEvent
        public static void onExplosionDetonate(
                net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
            Level level = event.getLevel();
            if (level.isClientSide) return;
            event.getAffectedBlocks().removeIf(pos -> {
                if (PsychicBeaconBlockEntity.isRewardChestProtectionActive(
                        level, level.getBlockEntity(pos))) {
                    return true;
                }
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                if (!state.is(ModRegistry.PSYCHIC_BEACON_BLOCK.get())) return false;
                BlockPos beaconPos = state.getValue(
                        com.mymod.flux_turret.block.PsychicBeaconBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                        ? pos.below() : pos;
                return level.getBlockEntity(beaconPos) instanceof PsychicBeaconBlockEntity beacon
                        && (beacon.isBattleInProgress() || beacon.isPendingReward());
            });
        }
    }
}
