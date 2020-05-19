package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingTaskError;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CalculationResult {

    private final NonNullList<ItemStack> missingItemStacks = NonNullList.create();
    private final NonNullList<FluidStack> missingFluidStacks = NonNullList.create();

    private CraftingTaskError error;

    public CalculationResult(CraftingTaskError error) {
        this.error = error;
    }

    /**
     * Adds the given {@code itemStacks} to the missing item stacks of this CalculationResult.
     * @param itemStacks the ItemStacks to add
     */
    public void addItemStacks(List<ItemStack> itemStacks) {
        for(ItemStack stack : missingItemStacks) {
            for(ItemStack newStack : itemStacks) {
                if(API.instance().getComparer().isEqualNoQuantity(stack, newStack)) {
                    stack.grow(newStack.getCount());
                    itemStacks.removeIf(i -> API.instance().getComparer().isEqualNoQuantity(i, newStack));
                    break;
                }
            }
        }
    }

    /**
     * Adds the given {@code fluidStacks} to the missing fluid stacks of this CalculationResult.
     * @param fluidStacks the FluidStacks to add
     */
    public void addFluidStacks(List<FluidStack> fluidStacks) {
        for(FluidStack stack : missingFluidStacks) {
            for(FluidStack newStack : fluidStacks) {
                if(FluidStack.areFluidStackTagsEqual(stack, newStack)) {
                    stack.amount += newStack.amount;
                    fluidStacks.removeIf(f -> FluidStack.areFluidStackTagsEqual(f, newStack));
                    break;
                }
            }
        }
    }

    /**
     * Merges all missing ItemStacks and FluidStacks of the given CalculationResult with this CalculationResult.
     * Also copies the error if there is none present.
     * @param other the result that should merged into the current one
     */
    public void merge(CalculationResult other) {
        this.addItemStacks(other.getMissingItemStacks());
        this.addFluidStacks(other.getMissingFluidStacks());

        if(other.getError() != null)
            this.error = other.getError();
    }

    @Nullable
    public CraftingTaskError getError() {
        return error;
    }

    @Nonnull
    public NonNullList<ItemStack> getMissingItemStacks() {
        return missingItemStacks;
    }

    @Nonnull
    public NonNullList<FluidStack> getMissingFluidStacks() {
        return missingFluidStacks;
    }
}
