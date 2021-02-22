package com.raoulvdberge.refinedstorage.api.network.grid;

import com.raoulvdberge.refinedstorage.api.util.IStackList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

import javax.annotation.Nullable;

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
    void onCrafted(IGridNetworkAware grid, IRecipe recipe, EntityPlayer player, @Nullable IStackList<ItemStack> availableItems, @Nullable IStackList<ItemStack> usedItems);

    /**
     * Default logic for crafting with shift click (mass crafting).
     *
     * @param grid   the grid
     * @param player the layer
     */
    void onCraftedShift(IGridNetworkAware grid, EntityPlayer player);

    /**
     * Default logic for transferring a recipe into the given {@code grid}.
     */
    void onRecipeTransfer(IGridNetworkAware grid, EntityPlayer player, ItemStack[][] recipe);

    /**
     * Default logic for clearing the crafting matrix
     */
    void onClear(IGridNetworkAware grid, EntityPlayer player);
}
