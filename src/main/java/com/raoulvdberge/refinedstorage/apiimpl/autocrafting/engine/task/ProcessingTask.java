package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;

import javax.annotation.Nonnull;

public class ProcessingTask extends Task {
    public ProcessingTask(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        super(pattern, amountNeeded, isFluidRequested);
    }

    @Override
    public void update() {

    }
}