package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingTaskError;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CalculationResult {

    private final IStackList<ItemStack> missingItemStacks = API.instance().createItemStackList();
    private final IStackList<FluidStack> missingFluidStacks = API.instance().createFluidStackList();

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

    /**
     * Merges all missing ItemStacks and FluidStacks of the given CalculationResult with this CalculationResult.
     * Also copies the error if there is none present.
     *
     * @param other the result that should merged into the current one
     */
    public void merge(@Nonnull CalculationResult other) {
        for (StackListEntry<FluidStack> entry : other.missingFluidStacks.getStacks())
            this.missingFluidStacks.add(entry.getStack());

        for (StackListEntry<ItemStack> entry : other.missingItemStacks.getStacks())
            this.missingItemStacks.add(entry.getStack());

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
    public IStackList<ItemStack> getMissingItemStacks() {
        return missingItemStacks;
    }

    @Nonnull
    public IStackList<FluidStack> getMissingFluidStacks() {
        return missingFluidStacks;
    }
}
