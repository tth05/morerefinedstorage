package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an input which is satisfied with a small amount to start, but then does need to get restocked during
 * crafting. This type of input is not given by a sub task that produces this input but rather by tracked items which
 * get inserted.
 *
 * This input type is only allowed for Processing Tasks!
 */
public class RestockableInput extends Input {
    public RestockableInput(@Nonnull ItemStack itemStack, long amountNeeded) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), amountNeeded);
    }

    public void fixCounts(int quantityPerCraft) {
        this.quantityPerCraft = quantityPerCraft;
        super.setAmountNeeded(quantityPerCraft);
    }
}
