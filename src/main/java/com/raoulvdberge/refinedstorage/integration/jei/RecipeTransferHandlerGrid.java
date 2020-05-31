package com.raoulvdberge.refinedstorage.integration.jei;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import com.raoulvdberge.refinedstorage.network.MessageGridProcessingTransfer;
import com.raoulvdberge.refinedstorage.network.MessageGridTransfer;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeTransferHandlerGrid implements IRecipeTransferHandler {
    public static final long TRANSFER_SCROLL_DELAY_MS = 200;
    public static long LAST_TRANSFER;

    @Nonnull
    @Override
    public Class<? extends Container> getContainerClass() {
        return ContainerGrid.class;
    }

    @Override
    public IRecipeTransferError transferRecipe(@Nonnull Container container, @Nonnull IRecipeLayout recipeLayout, @Nonnull EntityPlayer player,
                                               boolean maxTransfer, boolean doTransfer) {
        IGrid grid = ((ContainerGrid) container).getGrid();

        if (doTransfer) {
            LAST_TRANSFER = System.currentTimeMillis();

            if (grid.getGridType() == GridType.PATTERN && !isCraftingRecipe(recipeLayout.getRecipeCategory())) {
                List<ItemStack> inputs = new LinkedList<>();
                List<ItemStack> outputs = new LinkedList<>();

                List<FluidStack> fluidInputs = new LinkedList<>();
                List<FluidStack> fluidOutputs = new LinkedList<>();

                for (IGuiIngredient<ItemStack> guiIngredient : recipeLayout.getItemStacks().getGuiIngredients()
                        .values()) {
                    if (guiIngredient != null && guiIngredient.getDisplayedIngredient() != null) {
                        ItemStack ingredient = guiIngredient.getDisplayedIngredient().copy();

                        if (guiIngredient.isInput()) {
                            inputs.add(ingredient);
                        } else {
                            outputs.add(ingredient);
                        }
                    }
                }

                for (IGuiIngredient<FluidStack> guiIngredient : recipeLayout.getFluidStacks().getGuiIngredients()
                        .values()) {
                    if (guiIngredient != null && guiIngredient.getDisplayedIngredient() != null) {
                        FluidStack ingredient = guiIngredient.getDisplayedIngredient().copy();

                        if (guiIngredient.isInput()) {
                            fluidInputs.add(ingredient);
                        } else {
                            fluidOutputs.add(ingredient);
                        }
                    }
                }

                RS.INSTANCE.network
                        .sendToServer(new MessageGridProcessingTransfer(inputs, outputs, fluidInputs, fluidOutputs));
            } else {
                RS.INSTANCE.network.sendToServer(new MessageGridTransfer(
                        recipeLayout.getItemStacks().getGuiIngredients(),
                        container.inventorySlots.stream().filter(s -> s.inventory instanceof InventoryCrafting)
                                .collect(Collectors.toList())
                ));
            }
        }

        return null;
    }

    private boolean isCraftingRecipe(IRecipeCategory<?> recipeCategory) {
        return recipeCategory.getUid().equals(VanillaRecipeCategoryUid.CRAFTING);
    }
}
