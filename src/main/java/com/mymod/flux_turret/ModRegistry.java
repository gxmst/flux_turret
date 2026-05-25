package com.mymod.flux_turret;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import com.mymod.flux_turret.block.GatlingTurretBlock;
import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.block.PrismTowerBlock;
import com.mymod.flux_turret.block.TeslaCoilBlock;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;

public class ModRegistry {
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
                        FluxTurretMod.MOD_ID);
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
                        FluxTurretMod.MOD_ID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
                        .create(ForgeRegistries.BLOCK_ENTITY_TYPES, FluxTurretMod.MOD_ID);
        public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
                        .create(Registries.CREATIVE_MODE_TAB, FluxTurretMod.MOD_ID);
        public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister
                        .create(ForgeRegistries.SOUND_EVENTS, FluxTurretMod.MOD_ID);

        public static final RegistryObject<net.minecraft.sounds.SoundEvent> GATLING_SHOOT = SOUNDS.register(
                        "gatling_shoot", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "gatling_shoot")));

        public static final RegistryObject<net.minecraft.sounds.SoundEvent> TESLA_SHOOT = SOUNDS.register(
                        "tesla_shoot", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "tesla_shoot")));

        public static final RegistryObject<net.minecraft.sounds.SoundEvent> PRISM_SHOOT = SOUNDS.register(
                        "prism_shoot", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "prism_shoot")));

        public static final RegistryObject<net.minecraft.sounds.SoundEvent> GRAND_CANNON_SHOOT = SOUNDS.register(
                        "grand_cannon_shoot", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "grand_cannon_shoot")));

        public static final RegistryObject<Block> PRISM_TOWER_BLOCK = BLOCKS.register("prism_tower",
                        () -> new PrismTowerBlock(BlockBehaviour.Properties.of().strength(5.0f).sound(SoundType.METAL)
                                        .noOcclusion()));
 
        public static final RegistryObject<Block> TESLA_COIL_BLOCK = BLOCKS.register("tesla_coil",
                        () -> new TeslaCoilBlock(BlockBehaviour.Properties.of().strength(5.0f).sound(SoundType.METAL)
                                        .noOcclusion()));
 
        public static final RegistryObject<Block> GATLING_TURRET_BLOCK = BLOCKS.register("gatling_turret",
                        () -> new GatlingTurretBlock(BlockBehaviour.Properties.of().strength(4.0f).sound(SoundType.METAL)
                                        .noOcclusion()));

        public static final RegistryObject<Block> GRAND_CANNON_BLOCK = BLOCKS.register("grand_cannon",
                        () -> new GrandCannonBlock(BlockBehaviour.Properties.of().strength(8.0f).sound(SoundType.METAL)
                                        .noOcclusion()));
 
        public static final RegistryObject<Item> PRISM_TOWER_ITEM = ITEMS.register("prism_tower",
                        () -> new BlockItem(PRISM_TOWER_BLOCK.get(), new Item.Properties()));
 
        public static final RegistryObject<Item> TESLA_COIL_ITEM = ITEMS.register("tesla_coil",
                        () -> new BlockItem(TESLA_COIL_BLOCK.get(), new Item.Properties()));
 
        public static final RegistryObject<Item> GATLING_TURRET_ITEM = ITEMS.register("gatling_turret",
                        () -> new BlockItem(GATLING_TURRET_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> GRAND_CANNON_ITEM = ITEMS.register("grand_cannon",
                        () -> new BlockItem(GRAND_CANNON_BLOCK.get(), new Item.Properties()));
 
        public static final RegistryObject<BlockEntityType<PrismTowerBlockEntity>> PRISM_TOWER_BE = BLOCK_ENTITY_TYPES
                        .register("prism_tower", () -> BlockEntityType.Builder
                                        .of(PrismTowerBlockEntity::new, PRISM_TOWER_BLOCK.get()).build(null));
 
        public static final RegistryObject<BlockEntityType<TeslaCoilBlockEntity>> TESLA_COIL_BE = BLOCK_ENTITY_TYPES
                        .register("tesla_coil", () -> BlockEntityType.Builder
                                        .of(TeslaCoilBlockEntity::new, TESLA_COIL_BLOCK.get()).build(null));
 
        public static final RegistryObject<BlockEntityType<GatlingTurretBlockEntity>> GATLING_TURRET_BE = BLOCK_ENTITY_TYPES
                        .register("gatling_turret", () -> BlockEntityType.Builder
                                        .of(GatlingTurretBlockEntity::new, GATLING_TURRET_BLOCK.get()).build(null));

        public static final RegistryObject<BlockEntityType<GrandCannonBlockEntity>> GRAND_CANNON_BE = BLOCK_ENTITY_TYPES
                        .register("grand_cannon", () -> BlockEntityType.Builder
                                        .of(GrandCannonBlockEntity::new, GRAND_CANNON_BLOCK.get()).build(null));

        public static final RegistryObject<CreativeModeTab> FLUX_TURRET_TAB = CREATIVE_MODE_TABS.register(
                        "flux_turret_tab",
                        () -> CreativeModeTab.builder()
                                        .title(Component.translatable("itemGroup.flux_turret"))
                                        .icon(() -> PRISM_TOWER_ITEM.get().getDefaultInstance())
                                        .displayItems((parameters, output) -> {
                                                output.accept(PRISM_TOWER_ITEM.get());
                                                output.accept(TESLA_COIL_ITEM.get());
                                                output.accept(GATLING_TURRET_ITEM.get());
                                                output.accept(GRAND_CANNON_ITEM.get());
                                        }).build());
 
        public static void register(IEventBus eventBus) {
                BLOCKS.register(eventBus);
                ITEMS.register(eventBus);
                BLOCK_ENTITY_TYPES.register(eventBus);
                CREATIVE_MODE_TABS.register(eventBus);
                SOUNDS.register(eventBus);
        }
}
