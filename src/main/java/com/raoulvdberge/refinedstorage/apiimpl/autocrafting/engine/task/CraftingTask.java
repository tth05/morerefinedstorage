package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class CraftingTask extends Task {

    public CraftingTask(@Nonnull ICraftingPattern pattern, long amountNeeded) {
        super(pattern, amountNeeded, false);

        if(pattern.isProcessing())
            throw new IllegalArgumentException("Processing pattern cannot be used for crafting task!");
    }

    @Override
    public void update() {
        //TODO: update code
    }

    //TODO: move to Task class and add fluid version or remove
    public long getMaxCraftableAmount(ItemStack stack) {
        //max iterations
        long min = Long.MAX_VALUE;
        for (Input input : this.inputs) {
            min = Math.min(min, input.getTotalInputAmount());
        }

        //output quantity
        long outputQuantity = 1;

        outer:
        for(Output output : this.outputs) {
            for (ItemStack itemStack : output.getItemStacks()) {
                if(API.instance().getComparer().isEqualNoQuantity(itemStack, stack)) {
                    outputQuantity = output.getQuantityPerCraft();
                    break outer;
                }
            }
        }

        return min * outputQuantity;
    }

}
