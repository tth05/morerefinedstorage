package com.raoulvdberge.refinedstorage.gui.grid.filtering;

import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;

import java.util.function.Predicate;

public class GridFilterCraftable implements Predicate<IGridStack> {
    private final boolean craftable;

    public GridFilterCraftable(boolean craftable) {
        this.craftable = craftable;
    }

    @Override
    public boolean test(IGridStack stack) {
        if (craftable) {
            return stack.isCraftable();
        } else {
            return !stack.isCraftable() && stack.getOtherId() == null;
        }
    }
}
