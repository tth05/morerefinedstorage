package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor;

import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementAttributeHolder;
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
    public void sort() {
        this.elements.sort((o1, o2) -> {
            ICraftingMonitorElementAttributeHolder one =
                    (ICraftingMonitorElementAttributeHolder) (o1 instanceof CraftingMonitorElementError ?
                            ((CraftingMonitorElementError) o1).getBase() : o1);
            ICraftingMonitorElementAttributeHolder two =
                    (ICraftingMonitorElementAttributeHolder) (o2 instanceof CraftingMonitorElementError ?
                            ((CraftingMonitorElementError) o2).getBase() : o2);

            if (one.getScheduled() > two.getScheduled())
                return -1;
            else if (one.getScheduled() < two.getScheduled())
                return 1;
            else if (one.getProcessing() > two.getProcessing())
                return -1;
            else if (one.getProcessing() < two.getProcessing())
                return 1;
            else if (one.getCrafting() > two.getCrafting())
                return -1;
            else if (one.getCrafting() < two.getCrafting())
                return 1;
            else if (one.getStored() > two.getStored())
                return -1;
            else if (one.getStored() < two.getStored())
                return 1;
            return 0;
        });
    }

    @Override
    public void clearEmptyElements() {
        this.elements.removeIf(e -> {
            ICraftingMonitorElementAttributeHolder element =
                    (ICraftingMonitorElementAttributeHolder) (e instanceof CraftingMonitorElementError ?
                            ((CraftingMonitorElementError) e).getBase() : e);
            return element.getStored() < 1 && element.getCrafting() < 1 && element.getProcessing() < 1 &&
                    element.getScheduled() < 1;
        });
    }

    @Override
    public List<ICraftingMonitorElement> getElements() {
        if (!currentStorageLists.isEmpty())
            commit();

        return elements;
    }
}
