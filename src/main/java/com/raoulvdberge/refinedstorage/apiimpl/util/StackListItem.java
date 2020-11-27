package com.raoulvdberge.refinedstorage.apiimpl.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.util.ItemStack2ObjectHashMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class StackListItem implements IStackList<ItemStack> {
    private final ItemStack2ObjectHashMap<StackListEntry<ItemStack>> stacks = new ItemStack2ObjectHashMap<>();
    private final Multimap<Item, ItemStackWrapper> stacksByItem = HashMultimap.create();
    private final Map<UUID, StackListEntry<ItemStack>> index = new HashMap<>();

    @Override
    public StackListResult<ItemStack> add(@Nonnull ItemStack stack, long size) {
        if (stack.isEmpty() || size <= 0) {
            throw new IllegalArgumentException("Cannot accept empty stack");
        }

        StackListEntry<ItemStack> entry = stacks.get(stack);
        if (entry != null) {
            entry.grow(size);

            return new StackListResult<>(entry.getStack().copy(), entry.getId(), size);
        }

        ItemStack newStack = stack.copy();
        StackListEntry<ItemStack> newEntry = new StackListEntry<>(newStack, size);

        stacks.put(newStack, newEntry);
        stacksByItem.put(stack.getItem(), new ItemStackWrapper(newStack));
        index.put(newEntry.getId(), newEntry);

        return new StackListResult<>(newEntry.getStack().copy(), newEntry.getId(), size);
    }

    @Override
    public StackListResult<ItemStack> add(@Nonnull ItemStack stack) {
        return add(stack, stack.getCount());
    }

    @Override
    public StackListResult<ItemStack> remove(@Nonnull ItemStack stack, long size) {
        StackListEntry<ItemStack> entry = stacks.get(stack);
        if (entry != null) {
            if (entry.getCount() - size <= 0) {
                stacks.remove(stack);
                stacksByItem.remove(stack.getItem(), new ItemStackWrapper(stack));
                index.remove(entry.getId());

                return new StackListResult<>(stack.copy(), entry.getId(), -entry.getCount());
            } else {
                entry.shrink(size);

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
        if((flags & IComparer.COMPARE_NBT) == IComparer.COMPARE_NBT &&
                (flags & IComparer.COMPARE_DAMAGE) == IComparer.COMPARE_DAMAGE) {
            return stacks.get(stack);
        }

        for (ItemStackWrapper key : stacksByItem.get(stack.getItem())) {
            StackListEntry<ItemStack> entry = stacks.get(key.getStack());

            if (API.instance().getComparer().isEqual(entry.getStack(), stack, flags)) {
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
        stacksByItem.clear();
        index.clear();
    }

    @Override
    public void clearCounts() {
        for (Map.Entry<ItemStack, StackListEntry<ItemStack>> entry : stacks.entrySet()) {
            entry.getValue().setCount(0);
        }
    }

    @Override
    public void clearEmpty() {
        for (Iterator<Map.Entry<ItemStack, StackListEntry<ItemStack>>> iterator = stacks.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<ItemStack, StackListEntry<ItemStack>> entry = iterator.next();
            if (entry.getValue().getCount() < 1) {
                iterator.remove();
                stacksByItem.remove(entry.getValue().getStack().getItem(), new ItemStackWrapper(entry.getKey()));
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

        for (Map.Entry<ItemStack, StackListEntry<ItemStack>> entry : stacks.entrySet()) {
            ItemStack newStack = entry.getValue().getStack().copy();

            StackListEntry<ItemStack> newEntry = new StackListEntry<>(entry.getValue().getId(), newStack, entry.getValue().getCount());
            list.stacks.put(newStack, newEntry);
            list.stacksByItem.put(newStack.getItem(), new ItemStackWrapper(newStack));
            list.index.put(entry.getValue().getId(), newEntry);
        }

        return list;
    }

    public static final class ItemStackWrapper {
        private final boolean isEmpty;
        private final int damage;
        private final Item item;
        private final NBTTagCompound nbt;
        private final int hashCode;
        private final ItemStack itemStack;

        public ItemStackWrapper(ItemStack template) {
            this.itemStack = template;

            this.isEmpty = template.isEmpty();
            this.item = template.getItem();
            this.damage = template.getItemDamage();
            NBTTagCompound originalNbt = template.getTagCompound();
            this.nbt = originalNbt == null ? null : originalNbt.copy();

            int hashCode1;
            hashCode1 = 31 + Boolean.hashCode(this.isEmpty);
            hashCode1 = 31 * hashCode1 + this.item.hashCode();
            hashCode1 = 31 * hashCode1 + (this.nbt == null ? 0 : this.nbt.hashCode());
            hashCode = hashCode1;
        }

        public ItemStack getStack() {
            return this.itemStack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemStackWrapper key = (ItemStackWrapper) o;
            return this.isEmpty == key.isEmpty &&
                    this.damage == key.damage &&
                    this.item.equals(key.item) &&
                    Objects.equals(this.nbt, key.nbt);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
