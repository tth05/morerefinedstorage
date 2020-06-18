package com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class StorageExternalItem implements IStorageExternal<ItemStack> {
    private final IExternalStorageContext context;
    private final Supplier<IItemHandler> handlerSupplier;
    private final boolean connectedToInterface;
    private final ExternalStorageCacheItem cache = new ExternalStorageCacheItem();

    public StorageExternalItem(IExternalStorageContext context, Supplier<IItemHandler> handlerSupplier, boolean connectedToInterface) {
        this.context = context;
        this.handlerSupplier = handlerSupplier;
        this.connectedToInterface = connectedToInterface;
    }

    public boolean isConnectedToInterface() {
        return connectedToInterface;
    }

    @Override
    public void update(INetwork network) {
        if (getAccessType() == AccessType.INSERT) {
            return;
        }

        cache.update(network, handlerSupplier.get());
    }

    @Override
    public long getCapacity() {
        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return 0;
        }

        long capacity = 0;

        for (int i = 0; i < handler.getSlots(); ++i) {
            capacity += handler.getSlotLimit(i);
        }

        return capacity;
    }

    @Override
    public Collection<ItemStack> getStacks() {
        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return Collections.emptyList();
        }

        List<ItemStack> stacks = new ArrayList<>();

        for (int i = 0; i < handler.getSlots(); ++i) {
            stacks.add(handler.getStackInSlot(i));
        }

        return stacks;
    }

    @Nullable
    @Override
    public ItemStack insert(@Nonnull ItemStack stack, int size, Action action) {
        IItemHandler handler = handlerSupplier.get();

        if (handler != null && context.acceptsItem(stack)) {
            return StackUtils.emptyToNull(ItemHandlerHelper.insertItem(handler, ItemHandlerHelper.copyStackWithSize(stack, size), action == Action.SIMULATE));
        }

        return ItemHandlerHelper.copyStackWithSize(stack, size);
    }

    @Nullable
    @Override
    public ItemStack extract(@Nonnull ItemStack stack, int size, int flags, Action action) {
        int remaining = size;

        ItemStack received = null;

        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return null;
        }

        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack slot = handler.getStackInSlot(i);

            if (!slot.isEmpty() && API.instance().getComparer().isEqual(slot, stack, flags)) {
                ItemStack got = handler.extractItem(i, remaining, action == Action.SIMULATE);

                if (!got.isEmpty()) {
                    if (received == null) {
                        received = got.copy();
                    } else {
                        received.grow(got.getCount());
                    }

                    remaining -= got.getCount();

                    if (remaining == 0) {
                        break;
                    }
                }
            }
        }

        return received;
    }

    @Override
    public int getStored() {
        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return 0;
        }

        int size = 0;

        for (int i = 0; i < handler.getSlots(); ++i) {
            size += handler.getStackInSlot(i).getCount();
        }

        return size;
    }

    @Override
    public int getPriority() {
        return context.getPriority();
    }

    @Override
    public AccessType getAccessType() {
        return context.getAccessType();
    }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, @Nullable ItemStack remainder) {
        if (getAccessType() == AccessType.INSERT) {
            return 0;
        }

        return remainder == null ? size : (size - remainder.getCount());
    }
}
