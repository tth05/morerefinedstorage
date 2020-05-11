package com.raoulvdberge.refinedstorage.api.network.grid;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

/**
 * Defines default behavior of crafting grids.
 */
public interface ICraftingGridBehavior {
    /**
     * Default logic for regular crafting.
     *
     * @param grid   the grid
     * @param recipe the recipe
     * @param player the player
     */
    void onCrafted(IGridNetworkAware grid, IRecipe recipe, EntityPlayer player);

    /**
     * Default logic for crafting with shift click (mass crafting).
     *
     * @param grid   the grid
     * @param player the layer
     */
    void onCraftedShift(IGridNetworkAware grid, IRecipe recipe, EntityPlayer player);

    /**
     * Default logic for transferring a recipe into the given {@code grid}.
     */
    void onRecipeTransfer(IGridNetworkAware grid, EntityPlayer player, ItemStack[][] recipe);
}
