package com.raoulvdberge.refinedstorage.gui.grid.view;

import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.sorting.IGridSorter;
import com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackFluid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;

import java.util.List;

public class GridViewFluid extends GridViewBase {
    public GridViewFluid(GuiGrid gui, IGridSorter defaultSorter, List<IGridSorter> sorters) {
        super(gui, defaultSorter, sorters);
    }

    @Override
    public void setStacks(List<IGridStack> stacks) {
        map.clear();

        for (IGridStack stack : stacks) {
            // Don't let a craftable stack override a normal stack
//            if (stack.doesDisplayCraftText() && map.containsKey(stack.getId())) {
//                continue;
//            }

            map.put(stack.getId(), stack);
        }
    }

    @Override
    public void postChange(IGridStack stack, int delta) {
        if (!(stack instanceof GridStackFluid)) {
            return;
        }

        // Update the other id reference if needed.
        // Taking a stack out - and then re-inserting it - gives the new stack a new ID
        // With that new id, the reference for the crafting stack would be outdated.
        if (!stack.isCraftable() &&
                stack.getOtherId() != null) {
            map.get(stack.getOtherId()).updateOtherId(stack.getId());
        }

        GridStackFluid existing = (GridStackFluid) map.get(stack.getId());

        if (existing == null) {
            ((GridStackFluid) stack).getStack().amount = delta;

            map.put(stack.getId(), stack);
        } else {
            if (existing.getStack().amount + delta <= 0) {
                existing.getStack().amount += delta;

                map.remove(existing.getId());
            } else {
                existing.getStack().amount += delta;
            }

            existing.setTrackerEntry(stack.getTrackerEntry());
        }
    }
}
