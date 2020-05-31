package com.raoulvdberge.refinedstorage.api.autocrafting.task;

/**
 * Returned from {@link ICraftingTask#calculate()} when an error occurs during the calculation.
 */
public interface ICraftingTaskError {
    /**
     * @return the type
     */
    CraftingTaskErrorType getType();
}
