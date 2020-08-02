package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.google.common.collect.ArrayListMultimap;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StackListFluid implements IStackList<FluidStack> {
    private final ArrayListMultimap<Fluid, StackListEntry<FluidStack>> stacks = ArrayListMultimap.create();
    private final Map<UUID, StackListEntry<FluidStack>> index = new HashMap<>();

    @Override
    public StackListResult<FluidStack> add(@Nonnull FluidStack stack, long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Cannot accept empty stack");
        }

        for (StackListEntry<FluidStack> entry : stacks.get(stack.getFluid())) {
            FluidStack otherStack = entry.getStack();

            if (stack.isFluidEqual(otherStack)) {
                entry.grow(size);

                return new StackListResult<>(otherStack, entry.getId(), size);
            }
        }

        FluidStack newStack = stack.copy();
        StackListEntry<FluidStack> newEntry = new StackListEntry<>(newStack, newStack.amount);

        stacks.put(newStack.getFluid(), newEntry);
        index.put(newEntry.getId(), newEntry);

        return new StackListResult<>(newStack, newEntry.getId(), size);
    }

    @Override
    public StackListResult<FluidStack> add(@Nonnull FluidStack stack) {
        return add(stack, stack.amount);
    }

    @Override
    public StackListResult<FluidStack> remove(@Nonnull FluidStack stack, long size) {
        for (StackListEntry<FluidStack> entry : stacks.get(stack.getFluid())) {
            FluidStack otherStack = entry.getStack();

            if (stack.isFluidEqual(otherStack)) {
                if (entry.getCount() - size <= 0) {
                    stacks.remove(otherStack.getFluid(), entry);
                    index.remove(entry.getId());

                    return new StackListResult<>(otherStack, entry.getId(), -entry.getCount());
                } else {
                    entry.shrink(size);

                    return new StackListResult<>(otherStack, entry.getId(), -size);
                }
            }
        }

        return null;
    }

    @Override
    public StackListResult<FluidStack> remove(@Nonnull FluidStack stack) {
        return remove(stack, stack.amount);
    }

    @Override
    @Nullable
    public FluidStack get(@Nonnull FluidStack stack, int flags) {
        //TODO: check for get and getEntry calls
        for (StackListEntry<FluidStack> entry : stacks.get(stack.getFluid())) {
            FluidStack otherStack = entry.getStack();

            if (API.instance().getComparer().isEqual(otherStack, stack, flags)) {
                otherStack.amount = entry.getCount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) entry.getCount();
                return otherStack;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public StackListEntry<FluidStack> getEntry(@Nonnull FluidStack stack, int flags) {
        for (StackListEntry<FluidStack> entry : stacks.get(stack.getFluid())) {
            FluidStack otherStack = entry.getStack();

            if (API.instance().getComparer().isEqual(otherStack, stack, flags)) {
                return entry;
            }
        }

        return null;
    }

    @Override
    @Nullable
    public StackListEntry<FluidStack> get(UUID id) {
        return index.get(id);
    }

    @Override
    public void clear() {
        stacks.clear();
        index.clear();
    }

    @Override
    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    @Nonnull
    @Override
    public Collection<StackListEntry<FluidStack>> getStacks() {
        return stacks.values();
    }

    @Override
    @Nonnull
    public IStackList<FluidStack> copy() {
        StackListFluid list = new StackListFluid();

        for (StackListEntry<FluidStack> entry : stacks.values()) {

            StackListEntry<FluidStack> newEntry = new StackListEntry<>(entry.getId(), entry.getStack().copy(), entry.getCount());
            list.stacks.put(entry.getStack().getFluid(), newEntry);
            list.index.put(entry.getId(), newEntry);
        }

        return list;
    }
}
