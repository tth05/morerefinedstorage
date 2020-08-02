package com.raoulvdberge.refinedstorage.api.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.UUID;

/**
 * Contains the result of a stack list manipulation.
 *
 * @param <T> the stack type
 */
public class StackListResult<T> {
    private final T stack;
    private final UUID id;
    private long change;

    //TODO: add constructor without UUID
    public StackListResult(T stack, UUID id, long change) {
        this.stack = stack;
        this.id = id;
        this.change = change;
    }

    //TODO: add #getAndApply
    public void applyCount() {
        if(this.stack instanceof ItemStack)
            ((ItemStack) this.stack).setCount((int) getCount());
        else if(this.stack instanceof FluidStack)
            ((FluidStack) this.stack).amount = (int) getCount();
    }

    public void grow(long count) {
        if(this.change < 0)
            this.change -= count;
        else
            this.change += count;
    }

    public void shrink(long count) {
        if(this.change < 0)
            this.change = Math.min(0, this.change + count);
        else
            this.change = Math.max(0, this.change + count);
    }

    public void setCount(long count) {
        this.change = count;
    }

    /**
     * @return the stack
     */
    public T getStack() {
        return stack;
    }

    /**
     * @return the id of the {@link StackListEntry}
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return the change/delta value, is positive if this was a stack addition, or negative if it's a stack removal
     */
    public long getChange() {
        return change;
    }

    /**
     * @return the change as a positive value
     */
    public long getCount() {
        return Math.abs(change);
    }
}
