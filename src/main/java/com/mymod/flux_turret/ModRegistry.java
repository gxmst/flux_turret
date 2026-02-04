package com.mymod.flux_turret;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EntityType;
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
import com.mymod.flux_turret.block.PrismTowerBlock;
import com.mymod.flux_turret.block.entity.PrismTowerBlockEntity;

public class ModRegistry {
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
                        FluxTurretMod.MOD_ID);
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
                        FluxTurretMod.MOD_ID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
                        .create(ForgeRegistries.BLOCK_ENTITY_TYPES, FluxTurretMod.MOD_ID);
        public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
                        .create(ForgeRegistries.ENTITY_TYPES, FluxTurretMod.MOD_ID);
        public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
                        .create(Registries.CREATIVE_MODE_TAB, FluxTurretMod.MOD_ID);

        public static final RegistryObject<Block> PRISM_TOWER_BLOCK = BLOCKS.register("prism_tower",
                        () -> new PrismTowerBlock(BlockBehaviour.Properties.of().strength(5.0f).sound(SoundType.METAL)
                                        .noOcclusion()));

        public static final RegistryObject<Item> PRISM_TOWER_ITEM = ITEMS.register("prism_tower",
                        () -> new BlockItem(PRISM_TOWER_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<BlockEntityType<PrismTowerBlockEntity>> PRISM_TOWER_BE = BLOCK_ENTITY_TYPES
                        .register("prism_tower", () -> BlockEntityType.Builder
                                        .of(PrismTowerBlockEntity::new, PRISM_TOWER_BLOCK.get()).build(null));

        public static final RegistryObject<CreativeModeTab> FLUX_TURRET_TAB = CREATIVE_MODE_TABS.register(
                        "flux_turret_tab",
                        () -> CreativeModeTab.builder()
                                        .title(Component.translatable("itemGroup.flux_turret"))
                                        .icon(() -> PRISM_TOWER_ITEM.get().getDefaultInstance())
                                        .displayItems((parameters, output) -> {
                                                output.accept(PRISM_TOWER_ITEM.get());
                                        }).build());

        public static void register(IEventBus eventBus) {
                BLOCKS.register(eventBus);
                ITEMS.register(eventBus);
                BLOCK_ENTITY_TYPES.register(eventBus);
                ENTITY_TYPES.register(eventBus);
                CREATIVE_MODE_TABS.register(eventBus);
        }
}
