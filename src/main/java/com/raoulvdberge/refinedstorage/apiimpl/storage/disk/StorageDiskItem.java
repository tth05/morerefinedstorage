package com.raoulvdberge.refinedstorage.apiimpl.storage.disk;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory.StorageDiskFactoryItem;
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

    private final Multimap<Item, StackListEntry<ItemStack>> stacks = ArrayListMultimap.create();

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
            NBTTagCompound stackTag = entry.getStack().writeToNBT(new NBTTagCompound());
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
        for (StackListEntry<ItemStack> otherEntry : stacks.get(stack.getItem())) {
            if (API.instance().getComparer().isEqualNoQuantity(otherEntry.getStack(), stack)) {
                if (getCapacity() != -1 && getStored() + size > getCapacity()) {
                    long remainingSpace = getCapacity() - getStored();

                    if (remainingSpace <= 0) {
                        return new StackListResult<>(stack.copy(), size);
                    }

                    if (action == Action.PERFORM) {
                        otherEntry.grow(remainingSpace);
                        stored += remainingSpace;

                        onChanged();
                    }

                    return new StackListResult<>(otherEntry.getStack().copy(), size - remainingSpace);
                } else {
                    if (action == Action.PERFORM) {
                        otherEntry.grow(size);
                        stored += size;

                        onChanged();
                    }

                    return null;
                }
            }
        }

        if (getCapacity() != -1 && getStored() + size > getCapacity()) {
            long remainingSpace = getCapacity() - getStored();

            if (remainingSpace <= 0) {
                return new StackListResult<>(stack.copy(), size);
            }

            if (action == Action.PERFORM) {
                stacks.put(stack.getItem(), new StackListEntry<>(stack.copy(), remainingSpace));
                stored += remainingSpace;

                onChanged();
            }

            return new StackListResult<>(stack.copy(), size - remainingSpace);
        } else {
            if (action == Action.PERFORM) {
                stacks.put(stack.getItem(), new StackListEntry<>(stack.copy(), size));
                stored += size;

                onChanged();
            }

            return null;
        }
    }

    @Override
    @Nullable
    public StackListResult<ItemStack> extract(@Nonnull ItemStack stack, long size, int flags, Action action) {
        for (StackListEntry<ItemStack> otherEntry : stacks.get(stack.getItem())) {
            if (API.instance().getComparer().isEqual(otherEntry.getStack(), stack, flags)) {
                if (size > otherEntry.getCount()) {
                    size = otherEntry.getCount();
                }

                if (action == Action.PERFORM) {
                    if (otherEntry.getCount() - size == 0) {
                        stacks.remove(otherEntry.getStack().getItem(), otherEntry);
                        stored -= otherEntry.getCount();
                    } else {
                        otherEntry.shrink(size);
                        stored -= size;
                    }

                    onChanged();
                }

                return new StackListResult<>(otherEntry.getStack().copy(), size);
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

    public Multimap<Item, StackListEntry<ItemStack>> getRawStacks() {
        return stacks;
    }

    private void onChanged() {
        if (listener != null)
            listener.onChanged();

        if (world != null)
            API.instance().getStorageDiskManager(world).markForSaving();
    }
}
