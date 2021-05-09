package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StackListItem implements IStackList<ItemStack> {
    private final Map<ItemStackWrapper, StackListEntry<ItemStack>> stacks = new ConcurrentHashMap<>();
    private final Multimap<Item, ItemStackWrapper> stacksByItem = HashMultimap.create();
    private final Map<UUID, StackListEntry<ItemStack>> index = new HashMap<>();

    private long stored;

    @Override
    public StackListResult<ItemStack> add(@Nonnull ItemStack stack, long size) {
        if (stack.isEmpty() || size <= 0) {
            throw new IllegalArgumentException("Cannot accept empty stack");
        }

        ItemStackWrapper wrapper = new ItemStackWrapper(stack);

        StackListEntry<ItemStack> entry = stacks.get(wrapper);
        if (entry != null) {
            entry.grow(size);

            stored += size;

            return new StackListResult<>(entry.getStack().copy(), entry.getId(), size);
        }

        wrapper.setStack(stack.copy());
        StackListEntry<ItemStack> newEntry = new StackListEntry<>(wrapper.itemStack, size);

        stacks.put(wrapper, newEntry);
        stacksByItem.put(stack.getItem(), wrapper);
        index.put(newEntry.getId(), newEntry);

        stored += size;

        return new StackListResult<>(newEntry.getStack().copy(), newEntry.getId(), size);
    }

    @Override
    public StackListResult<ItemStack> add(@Nonnull ItemStack stack) {
        return add(stack, stack.getCount());
    }

    @Override
    public StackListResult<ItemStack> remove(@Nonnull ItemStack stack, long size) {
        ItemStackWrapper wrapper = new ItemStackWrapper(stack);

        StackListEntry<ItemStack> entry = stacks.get(wrapper);
        if (entry != null) {
            if (entry.getCount() - size <= 0) {
                stacks.remove(wrapper);
                stacksByItem.remove(stack.getItem(), wrapper);
                index.remove(entry.getId());

                stored -= entry.getCount();

                return new StackListResult<>(stack.copy(), entry.getId(), -entry.getCount());
            } else {
                entry.shrink(size);

                stored -= size;

                return new StackListResult<>(stack.copy(), entry.getId(), -size);
            }
        }

        return null;
    }

    @Override
    public StackListResult<ItemStack> remove(@Nonnull ItemStack stack) {
        return remove(stack, stack.getCount());
    }

    @Nullable
    @Override
    public StackListEntry<ItemStack> getEntry(@Nonnull ItemStack stack, int flags) {
        if ((flags & IComparer.COMPARE_NBT) == IComparer.COMPARE_NBT &&
            (flags & IComparer.COMPARE_DAMAGE) == IComparer.COMPARE_DAMAGE) {
            return stacks.get(new ItemStackWrapper(stack));
        }

        for (ItemStackWrapper key : stacksByItem.get(stack.getItem())) {
            StackListEntry<ItemStack> entry = stacks.get(key);

            if (API.instance().getComparer().isEqual(entry.getStack(), stack, flags)) {
                return entry.asUnmodifiable();
            }
        }

        return null;
    }

    @Override
    @Nullable
    public StackListEntry<ItemStack> get(UUID id) {
        StackListEntry<ItemStack> entry = index.get(id);
        if (entry == null)
            return null;
        return entry.asUnmodifiable();
    }

    @Override
    public void clear() {
        stacks.clear();
        stacksByItem.clear();
        index.clear();

        stored = 0;
    }

    @Override
    public void clearCounts() {
        for (StackListEntry<ItemStack> entry : stacks.values()) {
            entry.setCount(0);
        }

        stored = 0;
    }

    @Override
    public void clearEmpty() {
        for (Iterator<Map.Entry<ItemStackWrapper, StackListEntry<ItemStack>>> iterator = stacks.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<ItemStackWrapper, StackListEntry<ItemStack>> entry = iterator.next();
            StackListEntry<ItemStack> stackListEntry = entry.getValue();
            if (stackListEntry.getCount() < 1) {
                stacksByItem.remove(stackListEntry.getStack().getItem(), entry.getKey());
                index.remove(stackListEntry.getId());
                iterator.remove();
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
    public long getStored() {
        return this.stored;
    }

    @Override
    @Nonnull
    public IStackList<ItemStack> copy() {
        StackListItem list = new StackListItem();

        for (Map.Entry<ItemStackWrapper, StackListEntry<ItemStack>> entry : stacks.entrySet()) {
            ItemStack newStack = entry.getValue().getStack().copy();

            StackListEntry<ItemStack> newEntry = new StackListEntry<>(entry.getValue().getId(), newStack, entry.getValue().getCount());
            list.stacks.put(new ItemStackWrapper(newStack), newEntry);
            list.stacksByItem.put(newStack.getItem(), new ItemStackWrapper(newStack));
            list.index.put(entry.getValue().getId(), newEntry);

            list.stored += newEntry.getCount();
        }

        return list;
    }

    public static final class ItemStackWrapper {
        private final int hashCode;
        private ItemStack itemStack;

        public ItemStackWrapper(ItemStack template) {
            this.itemStack = template;

            boolean isEmpty = template.isEmpty();
            Item item = template.getItem();
            NBTTagCompound originalNbt = template.getTagCompound();
            NBTTagCompound nbt = originalNbt == null || originalNbt.isEmpty() ? null : originalNbt.copy();

            int hashCode1;
            hashCode1 = 31 + Boolean.hashCode(isEmpty);
            hashCode1 = 31 * hashCode1 + item.hashCode();
            hashCode1 = 31 * hashCode1 + (nbt == null ? 0 : nbt.hashCode());
            hashCode1 = 31 * hashCode1 + template.getItemDamage();
            hashCode = hashCode1;
        }

        public void setStack(ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        public ItemStack getStack() {
            return this.itemStack;
        }

        @Override
        public boolean equals(Object o) {
            return API.instance().getComparer().isEqualNoQuantity(this.itemStack, ((ItemStackWrapper) o).itemStack);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
