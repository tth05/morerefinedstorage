package com.raoulvdberge.refinedstorage.api.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Contains the result of a stack list manipulation.
 *
 * @param <T> the stack type
 */
public class StackListResult<T> {
    @Nonnull
    private final T stack;
    @Nullable
    private UUID id;
    private long change;

    public StackListResult(@Nonnull T stack, long change) {
        this.stack = stack;
        this.change = change;
    }

    public StackListResult(@Nonnull T stack, @Nonnull UUID id, long change) {
        this.stack = stack;
        this.id = id;
        this.change = change;
    }

    public static ItemStack nullToEmpty(@Nullable StackListResult<ItemStack> stackListResult) {
        return stackListResult == null ? ItemStack.EMPTY : stackListResult.getFixedStack();
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
     * @return returns the stack with {@link #getCount()} set as the stack size
     */
    public T getFixedStack() {
        int stackSize = getCount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) getCount();
        if(this.stack instanceof ItemStack)
            ((ItemStack) this.stack).setCount(stackSize);
        else if(this.stack instanceof FluidStack)
            ((FluidStack) this.stack).amount = stackSize;

        return this.stack;
    }

    /**
     * @return the stack
     */
    @Nonnull
    public T getStack() {
        return stack;
    }

    /**
     * @return the id of the {@link StackListEntry}
     */
    @Nullable
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
