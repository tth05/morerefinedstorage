package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * Represents a processing task
 */
public class ProcessingTask extends Task {
    public ProcessingTask(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        super(pattern, amountNeeded, isFluidRequested);
    }

    @Override
    public int update(@Nonnull INetwork network, ICraftingPatternContainer container, int toCraft) {
        return 0;
    }

    @Override
    protected int supplyInput(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}