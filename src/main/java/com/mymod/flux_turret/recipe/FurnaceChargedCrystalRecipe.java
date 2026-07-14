package com.mymod.flux_turret.recipe;

import com.google.gson.JsonObject;
import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.TurretConfig;
import com.mymod.flux_turret.item.EnergyCrystalItem;
import com.mymod.flux_turret.util.ChargeHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/** Charges the cooked crystal by the same FE/t rate used above a lit furnace. */
public final class FurnaceChargedCrystalRecipe extends SmeltingRecipe {
    private FurnaceChargedCrystalRecipe(SmeltingRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), firstIngredient(recipe),
                recipe.getResultItem(RegistryAccess.EMPTY).copy(), recipe.getExperience(), recipe.getCookingTime());
    }

    private static Ingredient firstIngredient(SmeltingRecipe recipe) {
        return recipe.getIngredients().isEmpty() ? Ingredient.EMPTY : recipe.getIngredients().get(0);
    }

    private ItemStack createChargedResult(RegistryAccess registryAccess) {
        ItemStack result = super.getResultItem(registryAccess).copy();
        int energy = ChargeHelper.totalCharge(
                TurretConfig.ENERGY_CRYSTAL_CHARGE_RATE.get(), getCookingTime());
        EnergyCrystalItem.setEnergyStored(result, energy);
        return result;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return createChargedResult(registryAccess);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return createChargedResult(registryAccess);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRegistry.FURNACE_CHARGED_CRYSTAL_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<FurnaceChargedCrystalRecipe> {
        @Override
        public FurnaceChargedCrystalRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            return new FurnaceChargedCrystalRecipe(
                    RecipeSerializer.SMELTING_RECIPE.fromJson(recipeId, json));
        }

        @Override
        public FurnaceChargedCrystalRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            SmeltingRecipe recipe = RecipeSerializer.SMELTING_RECIPE.fromNetwork(recipeId, buffer);
            return recipe == null ? null : new FurnaceChargedCrystalRecipe(recipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FurnaceChargedCrystalRecipe recipe) {
            RecipeSerializer.SMELTING_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
