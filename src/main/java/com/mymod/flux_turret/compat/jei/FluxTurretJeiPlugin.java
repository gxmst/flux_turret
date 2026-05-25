package com.mymod.flux_turret.compat.jei;

import com.mymod.flux_turret.FluxTurretMod;
import com.mymod.flux_turret.ModRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;
import java.util.stream.Collectors;

@JeiPlugin
public class FluxTurretJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(FluxTurretMod.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new TurretRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager recipeManager = Minecraft.getInstance().level.getRecipeManager();
        List<ShapedRecipe> turretRecipes = recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream()
                .filter(r -> r.getId().getNamespace().equals(FluxTurretMod.MOD_ID))
                .filter(r -> r instanceof ShapedRecipe)
                .map(r -> (ShapedRecipe) r)
                .collect(Collectors.toList());
        registration.addRecipes(TurretRecipeCategory.RECIPE_TYPE, turretRecipes);
    }
}
