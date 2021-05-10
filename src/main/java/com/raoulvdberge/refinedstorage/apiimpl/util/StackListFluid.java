package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StackListFluid implements IStackList<FluidStack> {
    private final Map<FluidStackWrapper, StackListEntry<FluidStack>> stacks = new ConcurrentHashMap<>();
    private final Map<UUID, StackListEntry<FluidStack>> index = new HashMap<>();

    private long stored;

    @Override
    public StackListResult<FluidStack> add(@Nonnull FluidStack stack, long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Cannot accept empty stack");
        }

        FluidStackWrapper wrapper = new FluidStackWrapper(stack);

        StackListEntry<FluidStack> entry = stacks.get(wrapper);
        if (entry != null) {
            FluidStack otherStack = entry.getStack();

            entry.grow(size);
            stored += size;

            return new StackListResult<>(otherStack.copy(), entry.getId(), size);
        }

        FluidStack newStack = stack.copy();
        wrapper.setStack(newStack);
        StackListEntry<FluidStack> newEntry = new StackListEntry<>(newStack, size);

        stacks.put(wrapper, newEntry);
        index.put(newEntry.getId(), newEntry);

        stored += size;

        return new StackListResult<>(newStack.copy(), newEntry.getId(), size);
    }

    @Override
    public StackListResult<FluidStack> add(@Nonnull FluidStack stack) {
        return add(stack, stack.amount);
    }

    @Override
    public StackListResult<FluidStack> remove(@Nonnull FluidStack stack, long size) {
        FluidStackWrapper wrapper = new FluidStackWrapper(stack);

        StackListEntry<FluidStack> entry = stacks.get(wrapper);
        if (entry != null) {
            FluidStack otherStack = entry.getStack();

            if (entry.getCount() - size <= 0) {
                stacks.remove(wrapper, entry);
                index.remove(entry.getId());

                stored -= entry.getCount();

                return new StackListResult<>(otherStack.copy(), entry.getId(), -entry.getCount());
            } else {
                entry.shrink(size);

                stored -= size;

                return new StackListResult<>(otherStack.copy(), entry.getId(), -size);
            }
        }

        return null;
    }

    @Override
    public StackListResult<FluidStack> remove(@Nonnull FluidStack stack) {
        return remove(stack, stack.amount);
    }

    @Nullable
    @Override
    public StackListEntry<FluidStack> getEntry(@Nonnull FluidStack stack, int flags) {
        StackListEntry<FluidStack> entry = stacks.get(new FluidStackWrapper(stack));

        //TODO: does not support extracting without nbt (weird for fluids anyway)
        return entry != null ? entry.asUnmodifiable() : null;
    }

    @Nullable
    @Override
    public FluidStack get(@Nonnull FluidStack stack, int flags) {
        StackListEntry<FluidStack> entry = getEntry(stack, flags);
        if (entry == null)
            return null;
        FluidStack copy = entry.getStack().copy();
        copy.amount = (int) Math.min(entry.getCount(), Integer.MAX_VALUE);
        return copy;
    }

    @Override
    @Nullable
    public StackListEntry<FluidStack> get(UUID id) {
        StackListEntry<FluidStack> entry = index.get(id);
        if (entry == null)
            return null;
        return entry.asUnmodifiable();
    }

    @Override
    public void clear() {
        stacks.clear();
        index.clear();

        stored = 0;
    }

    @Override
    public void clearCounts() {
        for (StackListEntry<FluidStack> entry : stacks.values()) {
            entry.setCount(0);
        }

        stored = 0;
    }

    @Override
    public void clearEmpty() {
        for (Iterator<Map.Entry<FluidStackWrapper, StackListEntry<FluidStack>>> iterator = stacks.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<FluidStackWrapper, StackListEntry<FluidStack>> entry = iterator.next();
            if (entry.getValue().getCount() < 1) {
                iterator.remove();
                index.remove(entry.getValue().getId());
            }
        }
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
    public long getStored() {
        return this.stored;
    }

    @Override
    @Nonnull
    public IStackList<FluidStack> copy() {
        StackListFluid list = new StackListFluid();

        for (StackListEntry<FluidStack> entry : stacks.values()) {

            StackListEntry<FluidStack> newEntry = new StackListEntry<>(entry.getId(), entry.getStack().copy(), entry.getCount());
            list.stacks.put(new FluidStackWrapper(entry.getStack()), newEntry);
            list.index.put(entry.getId(), newEntry);

            list.stored += newEntry.getCount();
        }

        return list;
    }

    public static final class FluidStackWrapper {
        private final int hashCode;
        private FluidStack fluidStack;

        public FluidStackWrapper(FluidStack template) {
            this.fluidStack = template;

            boolean isEmpty = template.amount < 1;
            Fluid fluid = template.getFluid();
            NBTTagCompound originalNbt = template.tag;
            NBTTagCompound nbt = originalNbt == null ? null : originalNbt.copy();

            int hashCode1;
            hashCode1 = 31 + Boolean.hashCode(isEmpty);
            hashCode1 = 31 * hashCode1 + fluid.hashCode();
            hashCode1 = 31 * hashCode1 + (nbt == null ? 0 : nbt.hashCode());
            hashCode1 = 31 * hashCode1 + template.hashCode();
            hashCode = hashCode1;
        }

        public void setStack(FluidStack fluidStack) {
            this.fluidStack = fluidStack;
        }

        public FluidStack getStack() {
            return this.fluidStack;
        }

        @Override
        public boolean equals(Object o) {
            return API.instance().getComparer().isEqual(this.fluidStack, ((FluidStackWrapper) o).fluidStack, IComparer.COMPARE_NBT);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
