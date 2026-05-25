package com.mymod.flux_turret.compat.jei;

import com.mymod.flux_turret.FluxTurretMod;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;

public class TurretRecipeCategory implements IRecipeCategory<ShapedRecipe> {
    public static final RecipeType<ShapedRecipe> RECIPE_TYPE = RecipeType.create(FluxTurretMod.MOD_ID, "turret_crafting", ShapedRecipe.class);
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("jei", "textures/gui/gui_vanilla.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;

    public TurretRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(TEXTURE, 0, 60, 116, 54);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(com.mymod.flux_turret.ModRegistry.PRISM_TOWER_ITEM.get()));
        this.title = Component.translatable("itemGroup.flux_turret");
    }

    @Override
    public RecipeType<ShapedRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ShapedRecipe recipe, IFocusGroup focuses) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe.getWidth();
        int height = recipe.getHeight();

        for (int i = 0; i < ingredients.size(); i++) {
            int row = i / width;
            int col = i % width;
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + col * 18, 1 + row * 18)
                    .addIngredients(ingredients.get(i));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19)
                .addItemStack(recipe.getResultItem(null));
    }
}
