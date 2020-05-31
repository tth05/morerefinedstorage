package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.raoulvdberge.refinedstorage.api.util.IFilter;
import net.minecraftforge.fluids.FluidStack;

public class FilterFluid implements IFilter<FluidStack> {
    private final FluidStack stack;
    private final int compare;
    private final int mode;
    private final boolean modFilter;

    public FilterFluid(FluidStack stack, int compare, int mode, boolean modFilter) {
        this.stack = stack;
        this.compare = compare;
        this.mode = mode;
        this.modFilter = modFilter;
    }

    @Override
    public FluidStack getStack() {
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
