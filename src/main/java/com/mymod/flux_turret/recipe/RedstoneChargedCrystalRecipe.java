package com.mymod.flux_turret.recipe;

import com.google.gson.JsonObject;
import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.EnergyCrystalItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Redstone charging in a crafting grid uses the same configured FE conversion
 * as right-click charging instead of relying on an untagged, formerly-full item.
 */
public final class RedstoneChargedCrystalRecipe extends ShapelessRecipe {
    private RedstoneChargedCrystalRecipe(ShapelessRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getResultItem(RegistryAccess.EMPTY).copy(), recipe.getIngredients());
    }

    private ItemStack createChargedResult(RegistryAccess registryAccess) {
        ItemStack result = super.getResultItem(registryAccess).copy();
        EnergyCrystalItem.setEnergyStored(result, TurretConfig.ENERGY_CRYSTAL_REDSTONE_BLOCK_CHARGE.get());
        return result;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return createChargedResult(registryAccess);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return createChargedResult(registryAccess);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRegistry.REDSTONE_CHARGED_CRYSTAL_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<RedstoneChargedCrystalRecipe> {
        @Override
        public RedstoneChargedCrystalRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            return new RedstoneChargedCrystalRecipe(
                    RecipeSerializer.SHAPELESS_RECIPE.fromJson(recipeId, json));
        }

        @Override
        public RedstoneChargedCrystalRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            ShapelessRecipe recipe = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(recipeId, buffer);
            return recipe == null ? null : new RedstoneChargedCrystalRecipe(recipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RedstoneChargedCrystalRecipe recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
