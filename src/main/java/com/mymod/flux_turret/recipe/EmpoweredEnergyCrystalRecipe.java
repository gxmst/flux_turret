package com.mymod.flux_turret.recipe;

import com.google.gson.JsonObject;
import com.mymod.flux_turret.ModRegistry;
import com.mymod.flux_turret.item.EnergyCrystalItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Keeps the source crystal's absolute FE when it is upgraded. */
public final class EmpoweredEnergyCrystalRecipe extends ShapedRecipe {
    private EmpoweredEnergyCrystalRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(),
                recipe.getResultItem(RegistryAccess.EMPTY).copy(), recipe.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = super.assemble(container, registryAccess);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack input = container.getItem(slot);
            if (input.is(ModRegistry.ENERGY_CRYSTAL_ITEM.get())) {
                EnergyCrystalItem.setEnergyStored(result, EnergyCrystalItem.getEnergyStored(input));
                break;
            }
        }
        return result;
    }

    /** Recipe-book/JEI previews must never expose an unversioned result as legacy-full. */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        ItemStack result = super.getResultItem(registryAccess).copy();
        EnergyCrystalItem.setEnergyStored(result, 0);
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRegistry.EMPOWERED_ENERGY_CRYSTAL_RECIPE.get();
    }

    public static final class Serializer implements RecipeSerializer<EmpoweredEnergyCrystalRecipe> {
        @Override
        public EmpoweredEnergyCrystalRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            return new EmpoweredEnergyCrystalRecipe(
                    RecipeSerializer.SHAPED_RECIPE.fromJson(recipeId, json));
        }

        @Override
        public EmpoweredEnergyCrystalRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            ShapedRecipe recipe = RecipeSerializer.SHAPED_RECIPE.fromNetwork(recipeId, buffer);
            return recipe == null ? null : new EmpoweredEnergyCrystalRecipe(recipe);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, EmpoweredEnergyCrystalRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
