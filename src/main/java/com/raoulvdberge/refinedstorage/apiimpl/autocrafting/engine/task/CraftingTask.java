package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import net.minecraft.item.ItemStack;

public class CraftingTask extends Task {

    public CraftingTask(ICraftingPattern pattern, long amountNeeded) {
        super(pattern);
        //task is not valid if pattern is null
        if(pattern == null)
            return;

        //TODO: processing tasks
        if(pattern.isProcessing())
            throw new UnsupportedOperationException();

        pattern.getInputs().forEach(i -> this.inputs.add(new Input(i, amountNeeded, 1, pattern.isOredict())));
        pattern.getFluidInputs().forEach(i -> this.inputs.add(new Input(i, amountNeeded, 1, pattern.isOredict())));

        pattern.getOutputs().forEach(o -> this.outputs.add(new Output(o, amountNeeded, o.getCount())));
        pattern.getFluidOutputs().forEach(o -> this.outputs.add(new Output(o, amountNeeded, o.amount)));

        this.valid = true;
    }

    public static CraftingTask fromItemStack(INetwork network, ItemStack output, long amountNeeded) {
        return new CraftingTask(network.getCraftingManager().getPattern(output), amountNeeded);
    }

    @Override
    public void update() {

    }

    @Override
    public CalculationResult calculate() {
        return null;
    }

}
