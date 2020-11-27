package com.raoulvdberge.refinedstorage.apiimpl.storage.disk;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory.StorageDiskFactoryItem;
import com.raoulvdberge.refinedstorage.apiimpl.util.StackListItem;
import com.raoulvdberge.refinedstorage.util.ItemStack2ObjectHashMap;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.stream.Collectors;

public class StorageDiskItem implements IStorageDisk<ItemStack> {
    public static final String NBT_VERSION = "Version";
    public static final String NBT_CAPACITY = "Capacity";
    public static final String NBT_ITEMS = "Items";
    public static final String NBT_REAL_SIZE = "RealSize";

    private final World world;
    private final long capacity;

    /**
     * tracks the amount of stored items
     */
    private long stored;

    private final ItemStack2ObjectHashMap<StackListEntry<ItemStack>> stacks = new ItemStack2ObjectHashMap<>();
    private final Multimap<Item, StackListItem.ItemStackWrapper> stacksByItem = HashMultimap.create();

    @Nullable
    private IStorageDiskListener listener;
    private IStorageDiskContainerContext context;

    public StorageDiskItem(@Nullable World world, long capacity) {
        this.world = world;
        this.capacity = capacity;
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList list = new NBTTagList();

        for (StackListEntry<ItemStack> entry : stacks.values()) {
            NBTTagCompound stackTag = StackUtils.serializeStackToNbt(entry.getStack());
            stackTag.setLong(NBT_REAL_SIZE, entry.getCount());
            list.appendTag(stackTag);
        }

        tag.setString(NBT_VERSION, RS.VERSION);
        tag.setTag(NBT_ITEMS, list);
        tag.setLong(NBT_CAPACITY, capacity);

        return tag;
    }

    @Override
    public String getId() {
        return StorageDiskFactoryItem.ID;
    }

    @Override
    public Collection<ItemStack> getStacks() {
        return stacks.values().stream().map(StackListEntry::getStack).collect(Collectors.toList());
    }

    @Override
    public Collection<StackListEntry<ItemStack>> getEntries() {
        return stacks.values();
    }

    @Override
    @Nullable
    public StackListResult<ItemStack> insert(@Nonnull ItemStack stack, long size, Action action) {
        StackListEntry<ItemStack> entry = stacks.get(stack);
        if (entry != null) {
            if (getCapacity() != -1 && getStored() + size > getCapacity()) {
                long remainingSpace = getCapacity() - getStored();

                if (remainingSpace <= 0) {
                    return new StackListResult<>(stack.copy(), size);
                }

                if (action == Action.PERFORM) {
                    entry.grow(remainingSpace);
                    stored += remainingSpace;

                    onChanged();
                }

                return new StackListResult<>(entry.getStack().copy(), size - remainingSpace);
            } else {
                if (action == Action.PERFORM) {
                    entry.grow(size);
                    stored += size;

                    onChanged();
                }

                return null;
            }
        }

        if (getCapacity() != -1 && getStored() + size > getCapacity()) {
            long remainingSpace = getCapacity() - getStored();

            if (remainingSpace <= 0) {
                return new StackListResult<>(stack.copy(), size);
            }

            if (action == Action.PERFORM) {
                ItemStack newStack = stack.copy();
                stacks.put(newStack, new StackListEntry<>(newStack, remainingSpace));
                stacksByItem.put(stack.getItem(), new StackListItem.ItemStackWrapper(newStack));
                stored += remainingSpace;

                onChanged();
            }

            return new StackListResult<>(stack.copy(), size - remainingSpace);
        } else {
            if (action == Action.PERFORM) {
                ItemStack newStack = stack.copy();
                stacks.put(newStack, new StackListEntry<>(newStack, size));
                stacksByItem.put(stack.getItem(), new StackListItem.ItemStackWrapper(newStack));
                stored += size;

                onChanged();
            }

            return null;
        }
    }

    @Override
    @Nullable
    public StackListResult<ItemStack> extract(@Nonnull ItemStack stack, long size, int flags, Action action) {
        //extract exact
        if ((flags & IComparer.COMPARE_NBT) == IComparer.COMPARE_NBT &&
                (flags & IComparer.COMPARE_DAMAGE) == IComparer.COMPARE_DAMAGE) {
            StackListEntry<ItemStack> entry = stacks.get(stack);

            if (entry == null)
                return null;

            if (size > entry.getCount()) {
                size = entry.getCount();
            }

            if (action == Action.PERFORM) {
                if (entry.getCount() - size == 0) {
                    stacks.remove(stack);
                    stacksByItem.remove(stack.getItem(), new StackListItem.ItemStackWrapper(stack));
                    stored -= entry.getCount();
                } else {
                    entry.shrink(size);
                    stored -= size;
                }

                onChanged();
            }

            return new StackListResult<>(entry.getStack().copy(), size);
        }

        for (StackListItem.ItemStackWrapper key : stacksByItem.get(stack.getItem())) {
            StackListEntry<ItemStack> entry = stacks.get(key.getStack());

            if (API.instance().getComparer().isEqual(entry.getStack(), stack, flags)) {
                if (size > entry.getCount()) {
                    size = entry.getCount();
                }

                if (action == Action.PERFORM) {
                    if (entry.getCount() - size == 0) {
                        stacks.remove(key.getStack());
                        stacksByItem.remove(stack.getItem(), key);
                        stored -= entry.getCount();
                    } else {
                        entry.shrink(size);
                        stored -= size;
                    }

                    onChanged();
                }

                return new StackListResult<>(entry.getStack().copy(), size);
            }
        }

        return null;
    }

    @Override
    public long getStored() {
        return this.stored;
    }

    /**
     * forces the stored amount to be re-calculated
     */
    public void calculateStoredAmount() {
        this.stored = stacks.values().stream().mapToLong(StackListEntry::getCount).sum();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public AccessType getAccessType() {
        return context.getAccessType();
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public void setSettings(@Nullable IStorageDiskListener listener, IStorageDiskContainerContext context) {
        this.listener = listener;
        this.context = context;
    }

    @Override
    public long getCacheDelta(long storedPreInsertion, long size, long remainder) {
        if (getAccessType() == AccessType.INSERT) {
            return 0;
        }

        return remainder < 1 ? size : (size - remainder);
    }

    /**
     * Convenience method for {@link StorageDiskFactoryItem}. Do not use this.
     */
    @Deprecated
    public void putRaw(ItemStack stack, long count) {
        stacks.put(stack, new StackListEntry<>(stack, count));
        stacksByItem.put(stack.getItem(), new StackListItem.ItemStackWrapper(stack));
    }

    private void onChanged() {
        if (listener != null)
            listener.onChanged();

        if (world != null)
            API.instance().getStorageDiskManager(world).markForSaving();
    }
}
