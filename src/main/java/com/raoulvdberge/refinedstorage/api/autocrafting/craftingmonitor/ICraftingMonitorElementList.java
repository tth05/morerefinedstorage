package com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor;

import java.util.List;

public interface ICraftingMonitorElementList {

    /**
     * Add a element to the list, similar elements will be merged.
     *
     * @param element the {@link ICraftingMonitorElement}
     */
    void add(ICraftingMonitorElement element);

    /**
     * Finishes a current merge operation.
     */
    void commit();

    /**
     * Sorts the committed elements according to the following order:
     * <ul>
     *     <li>Scheduled</li>
     *     <li>Processing</li>
     *     <li>Crafting</li>
     *     <li>Stored</li>
     * </ul>
     */
    void sort();

    /**
     * Removes all elements in this list which are completely empty (All counts are below 1).
     * This method only operates on already committed elements.
     */
    void clearEmptyElements();

    /**
     * Gets all the elements in the list.
     * This also commits the last changes.
     *
     * @return the current list of elements
     */
    List<ICraftingMonitorElement> getElements();
}
