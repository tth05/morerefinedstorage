package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an Input that is infinite and only needs to be extracted once. Only allowed for crafting tasks currently.
 *
 * //TODO: add support for processing tasks by checking if an input can be found in the output.
 * //TODO: add support for fluids (probably completely useless)
 */
public class InfiniteInput extends Input {
    public InfiniteInput(@Nonnull ItemStack itemStack, boolean oredict) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), 1, oredict);
    }

    @Override
    public long getAmountNeeded() {
        return 1;
    }

    @Override
    public void setAmountNeeded(long amountNeeded) {
        //NO OP
    }
}
