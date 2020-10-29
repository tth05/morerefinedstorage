package com.raoulvdberge.refinedstorage.api.autocrafting.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a crafting task.
 */
public interface ICraftingTask {
    /**
     * Calculates what this task will do, but doesn't run the task yet.
     *
     * @return the error, or null if there was no error
     */
    @Nullable
    ICraftingTaskError calculate();

    /**
     * Updates this task.
     * {@link ICraftingTask#calculate()} must be run before this!
     *
     * @return whether or not this task is finished
     */
    boolean update(Map<ICraftingPatternContainer, Integer> updateCountMap);

    /**
     * Called when this task is cancelled.
     */
    void onCancelled();

    /**
     * @return the amount of items that have to be crafted
     */
    long getQuantity();

    /**
     * @return the completion percentage
     */
    default int getCompletionPercentage() {
        return 0;
    }

    /**
     * @return the stack requested
     */
    ICraftingRequestInfo getRequested();

    /**
     * Called when a stack is inserted into the system through {@link com.raoulvdberge.refinedstorage.api.network.INetwork#insertItemTracked(ItemStack, int)}.
     *
     * @param stack         the stack
     * @param trackedAmount the amount of the stack that already has been tracked
     * @return see {@link com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.ProcessingTask#supplyOutput(ItemStack, int)}
     */
    int onTrackedInsert(ItemStack stack, int trackedAmount);

    /**
     * Called when a stack is inserted into the system through {@link com.raoulvdberge.refinedstorage.api.network.INetwork#insertFluidTracked(FluidStack, int)}.
     *
     * @param stack         the stack
     * @param trackedAmount the amount of the stack that already has been tracked
     * @return see {@link com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.ProcessingTask#supplyOutput(FluidStack, int)}
     */
    int onTrackedInsert(FluidStack stack, int trackedAmount);

    /**
     * Writes this task to NBT.
     *
     * @param tag the tag
     * @return the written tag
     */
    NBTTagCompound writeToNbt(NBTTagCompound tag);

    /**
     * {@link ICraftingTask#calculate()} must be run before this!
     *
     * @return the elements of this task for display in the crafting monitor
     */
    List<ICraftingMonitorElement> getCraftingMonitorElements();

    /**
     * {@link ICraftingTask#calculate()} must be run before this!
     *
     * @return get a list of {@link ICraftingPreviewElement}s
     */
    List<ICraftingPreviewElement<?>> getPreviewStacks();

    /**
     * @return the crafting pattern corresponding to this task
     */
    ICraftingPattern getPattern();

    /**
     * @return the time in ms when this task has started
     */
    long getExecutionStarted();

    /**
     * @return the total time that this task has been running for in ms
     */
    default long getExecutionTime() {
        if (getExecutionStarted() == -1)
            return -1;
        return System.currentTimeMillis() - getExecutionStarted();
    }

    /**
     * @return the time it took for the calculation to complete in ms or -1 if the calculation failed/hasn't completed.
     */
    long getCalculationTime();

    /**
     * @return the missing items
     */
    IStackList<ItemStack> getMissing();

    /**
     * @return the missing fluids
     */
    IStackList<FluidStack> getMissingFluids();

    /**
     * @return true if any items or fluids are missing, false otherwise
     */
    default boolean hasMissing() {
        return !getMissing().isEmpty() || !getMissingFluids().isEmpty();
    }

    void setCanUpdate(boolean canUpdate);

    /**
     * This is only false if a task hasn't actually been started. When a player requests a preview but hasn't started
     * the task yet, then this will return false.
     *
     * @return whether or not this task is allowed to be updated
     */
    boolean canUpdate();

    /**
     * @return the id of this task
     */
    UUID getId();
}
