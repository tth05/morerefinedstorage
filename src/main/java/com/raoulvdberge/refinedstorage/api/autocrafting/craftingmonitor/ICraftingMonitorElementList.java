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
     * Gets all the elements in the list.
     * This also commits the last changes.
     *
     * @return the current list of elements
     */
    List<ICraftingMonitorElement> getElements();
}
