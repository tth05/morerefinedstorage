package com.raoulvdberge.refinedstorage.apiimpl.storage.disk;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory.StorageDiskFactoryItem;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public class StorageDiskItem implements IStorageDisk<ItemStack> {
    public static final String NBT_VERSION = "Version";
    public static final String NBT_CAPACITY = "Capacity";
    public static final String NBT_ITEMS = "Items";

    private final World world;
    private final int capacity;

    /**
     * tracks the amount of stored items
     */
    private int stored;

    private final Multimap<Item, ItemStack> stacks = ArrayListMultimap.create();

    @Nullable
    private IStorageDiskListener listener;
    private IStorageDiskContainerContext context;

    public StorageDiskItem(@Nullable World world, int capacity) {
        this.world = world;
        this.capacity = capacity;
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList list = new NBTTagList();

        for (ItemStack stack : stacks.values()) {
            list.appendTag(StackUtils.serializeStackToNbt(stack));
        }

        tag.setString(NBT_VERSION, RS.VERSION);
        tag.setTag(NBT_ITEMS, list);
        tag.setInteger(NBT_CAPACITY, capacity);

        return tag;
    }

    @Override
    public String getId() {
        return StorageDiskFactoryItem.ID;
    }

    @Override
    public Collection<ItemStack> getStacks() {
        return stacks.values();
    }

    @Override
    @Nullable
    public ItemStack insert(@Nonnull ItemStack stack, int size, Action action) {
        for (ItemStack otherStack : stacks.get(stack.getItem())) {
            if (API.instance().getComparer().isEqualNoQuantity(otherStack, stack)) {
                if (getCapacity() != -1 && getStored() + size > getCapacity()) {
                    int remainingSpace = getCapacity() - getStored();

                    if (remainingSpace <= 0) {
                        return ItemHandlerHelper.copyStackWithSize(stack, size);
                    }

                    if (action == Action.PERFORM) {
                        otherStack.grow(remainingSpace);
                        stored += remainingSpace;

                        onChanged();
                    }

                    return ItemHandlerHelper.copyStackWithSize(otherStack, size - remainingSpace);
                } else {
                    if (action == Action.PERFORM) {
                        otherStack.grow(size);
                        stored += size;

                        onChanged();
                    }

                    return null;
                }
            }
        }

        if (getCapacity() != -1 && getStored() + size > getCapacity()) {
            int remainingSpace = getCapacity() - getStored();

            if (remainingSpace <= 0) {
                return ItemHandlerHelper.copyStackWithSize(stack, size);
            }

            if (action == Action.PERFORM) {
                stacks.put(stack.getItem(), ItemHandlerHelper.copyStackWithSize(stack, remainingSpace));
                stored += remainingSpace;

                onChanged();
            }

            return ItemHandlerHelper.copyStackWithSize(stack, size - remainingSpace);
        } else {
            if (action == Action.PERFORM) {
                stacks.put(stack.getItem(), ItemHandlerHelper.copyStackWithSize(stack, size));
                stored += size;

                onChanged();
            }

            return null;
        }
    }

    @Override
    @Nullable
    public ItemStack extract(@Nonnull ItemStack stack, int size, int flags, Action action) {
        for (ItemStack otherStack : stacks.get(stack.getItem())) {
            if (API.instance().getComparer().isEqual(otherStack, stack, flags)) {
                if (size > otherStack.getCount()) {
                    size = otherStack.getCount();
                }

                if (action == Action.PERFORM) {
                    if (otherStack.getCount() - size == 0) {
                        stacks.remove(otherStack.getItem(), otherStack);
                        stored -= otherStack.getCount();
                    } else {
                        otherStack.shrink(size);
                        stored -= size;
                    }

                    onChanged();
                }

                return ItemHandlerHelper.copyStackWithSize(otherStack, size);
            }
        }

        return null;
    }

    @Override
    public int getStored() {
        return this.stored;
    }

    /**
     * forces the stored amount to be re-calculated
     */
    public void calculateStoredAmount() {
        this.stored = stacks.values().stream().mapToInt(ItemStack::getCount).sum();
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
    public int getCapacity() {
        return capacity;
    }

    @Override
    public void setSettings(@Nullable IStorageDiskListener listener, IStorageDiskContainerContext context) {
        this.listener = listener;
        this.context = context;
    }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, @Nullable ItemStack remainder) {
        if (getAccessType() == AccessType.INSERT) {
            return 0;
        }

        return remainder == null ? size : (size - remainder.getCount());
    }

    public Multimap<Item, ItemStack> getRawStacks() {
        return stacks;
    }

    private void onChanged() {
        if (listener != null)
            listener.onChanged();

        if(world != null)
            API.instance().getStorageDiskManager(world).markForSaving();
    }
}
