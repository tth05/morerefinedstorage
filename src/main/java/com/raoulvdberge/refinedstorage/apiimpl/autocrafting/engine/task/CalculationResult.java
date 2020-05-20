package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingTaskError;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CalculationResult {

    private final NonNullList<ItemStack> missingItemStacks = NonNullList.create();
    private final NonNullList<FluidStack> missingFluidStacks = NonNullList.create();

    private final List<Task> newTasks = new ArrayList<>();

    private CraftingTaskError error;

    public CalculationResult() {
    }

    public CalculationResult(CraftingTaskError error) {
        this.error = error;
    }

    public void addNewTask(Task task) {
        this.newTasks.add(task);
    }

    public void addMissingItemStack(ItemStack itemStack) {
        for (ItemStack stack : missingItemStacks) {
            if (API.instance().getComparer().isEqualNoQuantity(stack, itemStack)) {
                stack.grow(itemStack.getCount());
                return;
            }
        }

        this.missingItemStacks.add(itemStack);
    }

    /**
     * Adds the given {@code itemStacks} to the missing item stacks of this CalculationResult.
     *
     * @param itemStacks the ItemStacks to add
     */
    public void addMissingItemStacks(List<ItemStack> itemStacks) {
        for (ItemStack newStack : itemStacks) {
            boolean merged = false;

            for (ItemStack stack : missingItemStacks) {
                if (API.instance().getComparer().isEqualNoQuantity(stack, newStack)) {
                    stack.grow(newStack.getCount());
                    itemStacks.removeIf(i -> API.instance().getComparer().isEqualNoQuantity(i, newStack));
                    merged = true;
                    break;
                }
            }

            if (!merged)
                this.missingItemStacks.add(newStack);
        }
    }

    public void addMissingFluidStack(FluidStack fluidStack) {
        for (FluidStack stack : missingFluidStacks) {
            if (FluidStack.areFluidStackTagsEqual(stack, fluidStack)) {
                stack.amount += fluidStack.amount;
                return;
            }
        }

        this.missingFluidStacks.add(fluidStack);
    }

    /**
     * Adds the given {@code fluidStacks} to the missing fluid stacks of this CalculationResult.
     *
     * @param fluidStacks the FluidStacks to add
     */
    public void addMissingFluidStacks(List<FluidStack> fluidStacks) {
        for (FluidStack newStack : fluidStacks) {
            boolean merged = false;

            for (FluidStack stack : missingFluidStacks) {
                if (FluidStack.areFluidStackTagsEqual(stack, newStack)) {
                    stack.amount += newStack.amount;
                    fluidStacks.removeIf(f -> FluidStack.areFluidStackTagsEqual(f, newStack));
                    merged = true;
                    break;
                }
            }

            if(!merged)
                this.missingFluidStacks.add(newStack);
        }
    }

    /**
     * Merges all missing ItemStacks and FluidStacks of the given CalculationResult with this CalculationResult.
     * Also copies the error if there is none present.
     *
     * @param other the result that should merged into the current one
     */
    public void merge(CalculationResult other) {
        this.addMissingItemStacks(other.getMissingItemStacks());
        this.addMissingFluidStacks(other.getMissingFluidStacks());

        if (other.getError() != null)
            this.error = other.getError();

        this.newTasks.addAll(other.getNewTasks());
    }

    @Nullable
    public CraftingTaskError getError() {
        return error;
    }

    public List<Task> getNewTasks() {
        return newTasks;
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
