package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.google.common.collect.ArrayListMultimap;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class StackListItem implements IStackList<ItemStack> {
    private final ArrayListMultimap<Item, StackListEntry<ItemStack>> stacks = ArrayListMultimap.create();
    private final Map<UUID, StackListEntry<ItemStack>> index = new HashMap<>();

    @Override
    public StackListResult<ItemStack> add(@Nonnull ItemStack stack, long size) {
        if (stack.isEmpty() || size <= 0) {
            throw new IllegalArgumentException("Cannot accept empty stack");
        }

        for (StackListEntry<ItemStack> entry : stacks.get(stack.getItem())) {
            ItemStack otherStack = entry.getStack();

            if (API.instance().getComparer().isEqualNoQuantity(otherStack, stack)) {
                entry.grow(size);

                return new StackListResult<>(otherStack.copy(), entry.getId(), size);
            }
        }

        StackListEntry<ItemStack> newEntry = new StackListEntry<>(stack.copy(), size);

        stacks.put(stack.getItem(), newEntry);
        index.put(newEntry.getId(), newEntry);

        return new StackListResult<>(newEntry.getStack().copy(), newEntry.getId(), size);
    }

    @Override
    public StackListResult<ItemStack> add(@Nonnull ItemStack stack) {
        return add(stack, stack.getCount());
    }

    @Override
    public StackListResult<ItemStack> remove(@Nonnull ItemStack stack, long size) {
        for (StackListEntry<ItemStack> entry : stacks.get(stack.getItem())) {
            ItemStack otherStack = entry.getStack();

            if (API.instance().getComparer().isEqualNoQuantity(otherStack, stack)) {
                if (entry.getCount() - size <= 0) {
                    stacks.remove(otherStack.getItem(), entry);
                    index.remove(entry.getId());

                    return new StackListResult<>(otherStack.copy(), entry.getId(), -entry.getCount());
                } else {
                    entry.shrink(size);

                    return new StackListResult<>(otherStack.copy(), entry.getId(), -size);
                }
            }
        }

        return null;
    }

    @Override
    public StackListResult<ItemStack> remove(@Nonnull ItemStack stack) {
        return remove(stack, stack.getCount());
    }

    @Override
    @Nullable
    public ItemStack get(@Nonnull ItemStack stack, int flags) {
        //TODO: check for get and getEntry calls
        for (StackListEntry<ItemStack> entry : stacks.get(stack.getItem())) {
            ItemStack otherStack = entry.getStack();

            if (API.instance().getComparer().isEqual(otherStack, stack, flags)) {
                otherStack.setCount(entry.getCount() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) entry.getCount());
                return otherStack;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public StackListEntry<ItemStack> getEntry(@Nonnull ItemStack stack, int flags) {
        for (StackListEntry<ItemStack> entry : stacks.get(stack.getItem())) {
            ItemStack otherStack = entry.getStack();

            if (API.instance().getComparer().isEqual(otherStack, stack, flags)) {
                return entry;
            }
        }

        return null;
    }

    @Override
    @Nullable
    public StackListEntry<ItemStack> get(UUID id) {
        return index.get(id);
    }

    @Override
    public void clear() {
        stacks.clear();
        index.clear();
    }

    @Override
    public void clearCounts() {
        for (Map.Entry<Item, StackListEntry<ItemStack>> entry : stacks.entries()) {
            entry.getValue().setCount(0);
        }
    }

    @Override
    public void clearEmpty() {
        for (Iterator<Map.Entry<Item, StackListEntry<ItemStack>>> iterator = stacks.entries().iterator(); iterator.hasNext(); ) {
            Map.Entry<Item, StackListEntry<ItemStack>> entry = iterator.next();
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
    public Collection<StackListEntry<ItemStack>> getStacks() {
        return stacks.values();
    }

    @Override
    @Nonnull
    public IStackList<ItemStack> copy() {
        StackListItem list = new StackListItem();

        for (StackListEntry<ItemStack> entry : stacks.values()) {
            ItemStack newStack = entry.getStack().copy();

            StackListEntry<ItemStack> newEntry = new StackListEntry<>(entry.getId(), newStack, entry.getCount());
            list.stacks.put(entry.getStack().getItem(), newEntry);
            list.index.put(entry.getId(), newEntry);
        }

        return list;
    }
}
