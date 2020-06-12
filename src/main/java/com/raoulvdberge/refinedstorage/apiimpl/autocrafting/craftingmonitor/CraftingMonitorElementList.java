package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor;

import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementList;

import java.util.*;

public class CraftingMonitorElementList implements ICraftingMonitorElementList {
    private final List<ICraftingMonitorElement> elements = new LinkedList<>();
    private final Map<String, Map<Integer, ICraftingMonitorElement>> currentStorageLists = new LinkedHashMap<>();

    @Override
    public void add(ICraftingMonitorElement element) {
        Map<Integer, ICraftingMonitorElement> storedElements = currentStorageLists.get(element.getBaseId());
        boolean merged = false;
        if (storedElements != null) {
            ICraftingMonitorElement existingElement = storedElements.get(element.baseElementHashCode());
            if (existingElement != null) {
                if (existingElement instanceof CraftingMonitorElementError) {
                    ((CraftingMonitorElementError) existingElement).mergeBases(element);
                } else if (element instanceof CraftingMonitorElementError) {
                    //merge the other way and override
                    ((CraftingMonitorElementError) element).mergeBases(existingElement);
                    storedElements.put(element.baseElementHashCode(), element);
                } else {
                    existingElement.merge(element);
                }
                merged = true;
            }
        }

        if (!merged) {
            if (storedElements == null) {
                storedElements = new HashMap<>();
            }
            storedElements.put(element.baseElementHashCode(), element);
            currentStorageLists.put(element.getBaseId(), storedElements);
        }
    }

    @Override
    public void commit() {
        currentStorageLists.values().stream().map(Map::values).flatMap(Collection::stream).forEach(elements::add);
        currentStorageLists.clear();
    }

    @Override
    public List<ICraftingMonitorElement> getElements() {
        if (!currentStorageLists.isEmpty())
            commit();

        return elements;
    }
}
