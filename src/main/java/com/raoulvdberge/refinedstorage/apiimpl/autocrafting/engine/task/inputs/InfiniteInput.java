package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an Input that is infinite and only needs to be extracted once. Only allowed for crafting tasks currently.
 */
public class InfiniteInput extends Input {

    private boolean containsItem;

    public InfiniteInput(@Nonnull ItemStack itemStack, boolean oredict) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), 1, oredict);
    }

    @Override
    public void decreaseItemStackAmount(long amount) {
        //NO OP
    }

    @Override
    public long getAmountNeeded() {
        return 1;
    }

    @Override
    public void setAmountNeeded(long amountNeeded) {
        //NO OP
    }

    @Override
    public int getQuantityPerCraft() {
        return 0;
    }

    @Override
    public long getMinCraftAmount() {
        return Long.MAX_VALUE;
    }

    /**
     * Whether or not this infinite input actually contains any item. If there are multiple infinite inputs for the
     * same item, then this will only be true for one of them.
     */
    public boolean containsItem() {
        return containsItem;
    }

    public void setContainsItem(boolean containsItem) {
        this.containsItem = containsItem;
    }
}
