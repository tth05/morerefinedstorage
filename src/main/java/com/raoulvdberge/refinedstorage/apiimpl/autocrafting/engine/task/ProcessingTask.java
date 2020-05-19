package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;

import javax.annotation.Nonnull;

public class ProcessingTask extends Task {
    public ProcessingTask(@Nonnull ICraftingPattern pattern) {
        super(pattern);
    }

    @Override
    public void update() {

    }

    @Override
    public CalculationResult calculate() {
        return null;
    }
}
