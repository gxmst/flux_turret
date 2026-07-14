package com.mymod.flux_turret;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import com.mymod.flux_turret.block.EnergyCrystalBlock;
import com.mymod.flux_turret.block.GatlingTurretBlock;
import com.mymod.flux_turret.block.GrandCannonBlock;
import com.mymod.flux_turret.block.PrismTowerBlock;
import com.mymod.flux_turret.block.PsychicBeaconBlock;
import com.mymod.flux_turret.block.TeslaCoilBlock;
import com.mymod.flux_turret.block.TurretExtensionBlock;
import com.mymod.flux_turret.block.entity.EnergyCrystalBlockEntity;
import com.mymod.flux_turret.block.entity.GatlingTurretBlockEntity;
import com.mymod.flux_turret.block.entity.GrandCannonBlockEntity;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;
import com.mymod.flux_turret.block.entity.PsychicBeaconBlockEntity;
import com.mymod.flux_turret.block.entity.TeslaCoilBlockEntity;
import com.mymod.flux_turret.item.EmptyCrystalItem;
import com.mymod.flux_turret.item.EnergyCrystalItem;
import com.mymod.flux_turret.item.TurretUpgradeModuleItem;
import com.mymod.flux_turret.item.TurretUpgradeType;
import com.mymod.flux_turret.menu.PsychicBeaconMenu;
import com.mymod.flux_turret.menu.TurretInspectorMenu;
import com.mymod.flux_turret.recipe.EmpoweredEnergyCrystalRecipe;
import com.mymod.flux_turret.recipe.FurnaceChargedCrystalRecipe;
import com.mymod.flux_turret.recipe.RedstoneChargedCrystalRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

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
        public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister
                        .create(ForgeRegistries.MENU_TYPES, FluxTurretMod.MOD_ID);
        public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
                        .create(ForgeRegistries.RECIPE_SERIALIZERS, FluxTurretMod.MOD_ID);

        public static final RegistryObject<RecipeSerializer<RedstoneChargedCrystalRecipe>> REDSTONE_CHARGED_CRYSTAL_RECIPE =
                        RECIPE_SERIALIZERS.register("redstone_charged_crystal",
                                        RedstoneChargedCrystalRecipe.Serializer::new);
        public static final RegistryObject<RecipeSerializer<EmpoweredEnergyCrystalRecipe>> EMPOWERED_ENERGY_CRYSTAL_RECIPE =
                        RECIPE_SERIALIZERS.register("empowered_energy_crystal",
                                        EmpoweredEnergyCrystalRecipe.Serializer::new);
        public static final RegistryObject<RecipeSerializer<FurnaceChargedCrystalRecipe>> FURNACE_CHARGED_CRYSTAL_RECIPE =
                        RECIPE_SERIALIZERS.register("furnace_charged_crystal",
                                        FurnaceChargedCrystalRecipe.Serializer::new);

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

        public static final RegistryObject<Block> ENERGY_CRYSTAL_BLOCK = BLOCKS.register("energy_crystal",
                        () -> new EnergyCrystalBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.AMETHYST)
                                        .noOcclusion().lightLevel(state -> state.getValue(EnergyCrystalBlock.FULL) ? 6 : 0)));

        public static final RegistryObject<Block> EMPOWERED_ENERGY_CRYSTAL_BLOCK = BLOCKS.register("empowered_energy_crystal",
                        () -> new EnergyCrystalBlock(BlockBehaviour.Properties.of().strength(4.0f).sound(SoundType.AMETHYST)
                                        .noOcclusion().lightLevel(state -> state.getValue(EnergyCrystalBlock.FULL) ? 10 : 0), 10));

        public static final RegistryObject<Block> PSYCHIC_BEACON_BLOCK = BLOCKS.register("psychic_beacon",
                        () -> new PsychicBeaconBlock(BlockBehaviour.Properties.of().strength(5.0f).sound(SoundType.METAL)
                                        .noOcclusion().lightLevel(state -> state.getValue(PsychicBeaconBlock.LIT) ? 6 : 0)));

        public static final RegistryObject<Block> TURRET_EXTENSION_BLOCK = BLOCKS.register("turret_extension",
                        () -> new TurretExtensionBlock(BlockBehaviour.Properties.of().strength(5.0F)
                                        .noOcclusion().noCollission().noLootTable()));
 
        public static final RegistryObject<Item> PRISM_TOWER_ITEM = ITEMS.register("prism_tower",
                        () -> new BlockItem(PRISM_TOWER_BLOCK.get(), new Item.Properties()));
 
        public static final RegistryObject<Item> TESLA_COIL_ITEM = ITEMS.register("tesla_coil",
                        () -> new BlockItem(TESLA_COIL_BLOCK.get(), new Item.Properties()));
 
        public static final RegistryObject<Item> GATLING_TURRET_ITEM = ITEMS.register("gatling_turret",
                        () -> new BlockItem(GATLING_TURRET_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> GRAND_CANNON_ITEM = ITEMS.register("grand_cannon",
                        () -> new BlockItem(GRAND_CANNON_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> ENERGY_CRYSTAL_ITEM = ITEMS.register("energy_crystal",
                        () -> new EnergyCrystalItem(ENERGY_CRYSTAL_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> EMPOWERED_ENERGY_CRYSTAL_ITEM = ITEMS.register("empowered_energy_crystal",
                        () -> new EnergyCrystalItem(EMPOWERED_ENERGY_CRYSTAL_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> EMPTY_CRYSTAL_ITEM = ITEMS.register("empty_crystal",
                        () -> new EmptyCrystalItem(ENERGY_CRYSTAL_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> PSYCHIC_BEACON_ITEM = ITEMS.register("psychic_beacon",
                        () -> new BlockItem(PSYCHIC_BEACON_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<Item> ARMOR_PIERCING_ROUNDS_MODULE = ITEMS.register("armor_piercing_rounds_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.ARMOR_PIERCING_ROUNDS, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> FIRE_ROUNDS_MODULE = ITEMS.register("fire_rounds_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.FIRE_ROUNDS, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> SLOW_ROUNDS_MODULE = ITEMS.register("slow_rounds_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.SLOW_ROUNDS, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> CHAIN_JUMP_MODULE = ITEMS.register("chain_jump_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.CHAIN_JUMP, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> EMP_SLOW_MODULE = ITEMS.register("emp_slow_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.EMP_SLOW, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> OVERLOAD_BURST_MODULE = ITEMS.register("overload_burst_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.OVERLOAD_BURST, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> FOCUSED_BEAM_MODULE = ITEMS.register("focused_beam_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.FOCUSED_BEAM, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> REFRACTION_BEAM_MODULE = ITEMS.register("refraction_beam_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.REFRACTION_BEAM, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> REMOTE_SUPPORT_MODULE = ITEMS.register("remote_support_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.REMOTE_SUPPORT, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> SEISMIC_SHOCK_MODULE = ITEMS.register("seismic_shock_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.SEISMIC_SHOCK, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> ARMOR_BREAK_MODULE = ITEMS.register("armor_break_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.ARMOR_BREAK, new Item.Properties().stacksTo(16)));

        public static final RegistryObject<Item> CLUSTER_SHELLS_MODULE = ITEMS.register("cluster_shells_module",
                        () -> new TurretUpgradeModuleItem(TurretUpgradeType.CLUSTER_SHELLS, new Item.Properties().stacksTo(16)));
 
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

        public static final RegistryObject<BlockEntityType<EnergyCrystalBlockEntity>> ENERGY_CRYSTAL_BE = BLOCK_ENTITY_TYPES
                        .register("energy_crystal", () -> BlockEntityType.Builder
                                        .of(EnergyCrystalBlockEntity::new, ENERGY_CRYSTAL_BLOCK.get(),
                                                        EMPOWERED_ENERGY_CRYSTAL_BLOCK.get()).build(null));

        public static final RegistryObject<BlockEntityType<PsychicBeaconBlockEntity>> PSYCHIC_BEACON_BE = BLOCK_ENTITY_TYPES
                        .register("psychic_beacon", () -> BlockEntityType.Builder
                                        .of(PsychicBeaconBlockEntity::new, PSYCHIC_BEACON_BLOCK.get()).build(null));

        public static final RegistryObject<MenuType<PsychicBeaconMenu>> PSYCHIC_BEACON_MENU = MENU_TYPES
                        .register("psychic_beacon", () -> IForgeMenuType.create(PsychicBeaconMenu::new));

        public static final RegistryObject<MenuType<TurretInspectorMenu>> TURRET_INSPECTOR_MENU = MENU_TYPES
                        .register("turret_inspector", () -> IForgeMenuType.create(TurretInspectorMenu::new));

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
                                                output.accept(EMPTY_CRYSTAL_ITEM.get());
                                                output.accept(EnergyCrystalItem.createChargedStack(
                                                                ENERGY_CRYSTAL_ITEM.get(),
                                                                TurretConfig.ENERGY_CRYSTAL_CAPACITY.get()));
                                                output.accept(EnergyCrystalItem.createChargedStack(
                                                                EMPOWERED_ENERGY_CRYSTAL_ITEM.get(),
                                                                TurretConfig.ENERGY_CRYSTAL_CAPACITY.get() * 10));
                                                output.accept(PSYCHIC_BEACON_ITEM.get());
                                                output.accept(ARMOR_PIERCING_ROUNDS_MODULE.get());
                                                output.accept(FIRE_ROUNDS_MODULE.get());
                                                output.accept(SLOW_ROUNDS_MODULE.get());
                                                output.accept(CHAIN_JUMP_MODULE.get());
                                                output.accept(EMP_SLOW_MODULE.get());
                                                output.accept(OVERLOAD_BURST_MODULE.get());
                                                output.accept(FOCUSED_BEAM_MODULE.get());
                                                output.accept(REFRACTION_BEAM_MODULE.get());
                                                output.accept(REMOTE_SUPPORT_MODULE.get());
                                                output.accept(SEISMIC_SHOCK_MODULE.get());
                                                output.accept(ARMOR_BREAK_MODULE.get());
                                                output.accept(CLUSTER_SHELLS_MODULE.get());
                                        }).build());
 
        public static void register(IEventBus eventBus) {
                BLOCKS.register(eventBus);
                ITEMS.register(eventBus);
                BLOCK_ENTITY_TYPES.register(eventBus);
                CREATIVE_MODE_TABS.register(eventBus);
                SOUNDS.register(eventBus);
                MENU_TYPES.register(eventBus);
                RECIPE_SERIALIZERS.register(eventBus);
        }
}
