package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetwork;

import javax.annotation.Nonnull;

public class ProcessingTask extends Task {
    public ProcessingTask(@Nonnull ICraftingPattern pattern, long amountNeeded) {
        super(pattern, amountNeeded);
    }

    @Override
    public void update() {

    }

    @Nonnull
    @Override
    public CalculationResult calculate(@Nonnull INetwork network) {
        return null;
    }
}