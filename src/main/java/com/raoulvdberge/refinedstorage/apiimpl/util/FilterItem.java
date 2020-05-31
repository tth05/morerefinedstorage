package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.raoulvdberge.refinedstorage.api.util.IFilter;
import net.minecraft.item.ItemStack;

public class FilterItem implements IFilter<ItemStack> {
    private final ItemStack stack;
    private final int compare;
    private final int mode;
    private final boolean modFilter;

    public FilterItem(ItemStack stack, int compare, int mode, boolean modFilter) {
        this.stack = stack;
        this.compare = compare;
        this.mode = mode;
        this.modFilter = modFilter;
    }

    @Override
    public ItemStack getStack() {
        return stack;
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public int getMode() {
        return mode;
    }

    @Override
    public boolean isModFilter() {
        return modFilter;
    }
}
