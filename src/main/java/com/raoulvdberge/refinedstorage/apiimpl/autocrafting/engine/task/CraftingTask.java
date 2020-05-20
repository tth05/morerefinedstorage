package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

public class CraftingTask extends Task {

    public CraftingTask(ICraftingPattern pattern, long amountNeeded) {
        super(pattern, amountNeeded);
        //task is not valid if pattern is null
        if(pattern == null)
            return;

        //TODO: processing tasks
        if(pattern.isProcessing())
            throw new UnsupportedOperationException();

        //add outputs
        int smallestOutputStackSize = Integer.MAX_VALUE;
        for (ItemStack itemStack : pattern.getOutputs()) {
            smallestOutputStackSize = Math.min(smallestOutputStackSize, itemStack.getCount());
            this.outputs.add(new Output(itemStack, itemStack.getCount()));
        }
        //calculate actual needed amount, basically the amount of iterations that have to be run
        amountNeeded = (long) Math.ceil((double)amountNeeded / (double)smallestOutputStackSize);

        pattern.getFluidOutputs().forEach(o -> this.outputs.add(new Output(o, o.amount)));

        //merge all pattern inputs
        for (NonNullList<ItemStack> itemStacks : pattern.getInputs()) {
            if(itemStacks.isEmpty())
                continue;

            Input newInput = new Input(itemStacks, amountNeeded, pattern.isOredict());

            boolean merged = false;
            for (Input input : this.inputs) {
                if(newInput.equals(input)) {
                    input.merge(newInput);
                    merged = true;
                }
            }

            if(!merged)
                this.inputs.add(newInput);
        }

        //merge all pattern fluid inputs
        for (FluidStack i : pattern.getFluidInputs()) {
            Input newInput = new Input(i, amountNeeded, pattern.isOredict());

            boolean merged = false;
            for (Input input : this.inputs) {
                if(newInput.equals(input)) {
                    input.merge(newInput);
                    merged = true;
                }
            }

            if(!merged)
                this.inputs.add(newInput);
        }

        this.valid = true;
    }

    @Override
    public void update() {

    }

    @Override
    @Nonnull
    public CalculationResult calculate(@Nonnull INetwork network) {
        CalculationResult result = new CalculationResult();

        inputLoop:
        for (Input input : this.inputs) {
            for(ItemStack ingredient : input.getItemStacks()) {
                ItemStack extracted = network.extractItem(ingredient, (int) input.getAmountMissing(), Action.PERFORM);
                if(extracted.isEmpty())
                    continue;

                long remainder = input.increaseAmount(extracted, extracted.getCount());
                if(remainder != -1) {
                    if(remainder != 0)
                        network.insertItem(ingredient, (int) remainder, Action.PERFORM);
                    continue inputLoop;
                }
            }

            //if input is not satisfied
            if(input.getAmountMissing() > 0) {
                ItemStack first = input.getItemStacks().get(0);

                ICraftingPattern pattern = network.getCraftingManager().getPattern(first);
                if(pattern != null) {
                    //TODO: processing pattern
                    if(pattern.isProcessing())
                        throw new UnsupportedOperationException();

                    CraftingTask newTask = new CraftingTask(pattern, input.getAmountMissing());
                    CalculationResult newTaskResult = newTask.calculate(network);

                    long max = newTask.getMaxCraftableAmount();
                    input.increaseAmount(first, max);

                    result.merge(newTaskResult);
                }
            }

            //if input cannot be satisfied
            if(input.getAmountMissing() > 0) {
                ItemStack missing = input.getItemStacks().get(0);
                missing.setCount((int) input.getAmountMissing());
                result.addMissingItemStack(missing);
            }
        }

        return result;
    }

    //TODO: move to Task class
    public long getMaxCraftableAmount() {
        long min = Long.MAX_VALUE;
        for (Input input : this.inputs) {
            min = Math.min(min, input.getTotalInputAmount());
        }

        return min;
    }

}
