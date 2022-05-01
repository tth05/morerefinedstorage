package com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage;

import com.cjm721.overloaded.storage.LongItemStack;
import com.cjm721.overloaded.storage.item.LongItemStorage;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.integration.overloaded.IntegrationOverloaded;
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

        cache.update(network, handlerSupplier.get(), (List<StackListEntry<ItemStack>>) getEntries());
    }

    @Override
    public long getCapacity() {
        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return 0;
        }

        long capacity = 0;

        if (IntegrationOverloaded.isLoaded() && handler instanceof LongItemStorage) {
            return Long.MAX_VALUE;
        }

        for (int i = 0; i < handler.getSlots(); ++i) {
            capacity += handler.getSlotLimit(i);
        }

        return capacity;
    }

    @Override
    public Collection<StackListEntry<ItemStack>> getEntries() {
        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return Collections.emptyList();
        }

        List<StackListEntry<ItemStack>> list = new ArrayList<>();

        if (IntegrationOverloaded.isLoaded() && handler instanceof LongItemStorage) {
            LongItemStorage longItemStorage = (LongItemStorage) handler;
            LongItemStack longItemStack = longItemStorage.status();
            list.add(new StackListEntry<>(longItemStack.getItemStack(), longItemStack.getAmount()));
            return list;
        }

        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack stack = handler.getStackInSlot(i);
            list.add(new StackListEntry<>(stack, stack.getCount()));
        }

        return list;
    }

    @Nullable
    @Override
    public StackListResult<ItemStack> insert(@Nonnull ItemStack stack, long size, Action action) {
        IItemHandler handler = handlerSupplier.get();

        if (handler != null && context.acceptsItem(stack)) {
            //TODO: remove cast
            ItemStack result = StackUtils.emptyToNull(
                    ItemHandlerHelper.insertItem(
                            handler,
                            ItemHandlerHelper.copyStackWithSize(stack, (int) size),
                            action == Action.SIMULATE));
            if (result == null)
                return null;
            return new StackListResult<>(result, result.getCount());
        }

        return new StackListResult<>(stack.copy(), size);
    }

    @Nullable
    @Override
    public StackListResult<ItemStack> extract(@Nonnull ItemStack stack, long size, int flags, Action action) {
        //TODO: remove cast
        int remaining = (int) size;

        ItemStack received = null;

        IItemHandler handler = handlerSupplier.get();

        if (handler == null) {
            return null;
        }

        outer:
        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack slot = handler.getStackInSlot(i);

            if (!slot.isEmpty() && API.instance().getComparer().isEqual(slot, stack, flags)) {
                int countInSlot = slot.getCount();
                ItemStack got = handler.extractItem(i, remaining, action == Action.SIMULATE);

                //Check if some handler has a limit on how much you can extract at once
                boolean shouldLoop = got.getCount() < remaining && got.getCount() != countInSlot;

                while (!got.isEmpty()) {
                    if (received == null) {
                        received = got.copy();
                    } else {
                        received.grow(got.getCount());
                    }

                    remaining -= got.getCount();

                    if (remaining <= 0)
                        break outer;

                    if (!shouldLoop)
                        break;

                    got = handler.extractItem(i, remaining, action == Action.SIMULATE);
                }
            }
        }

        return received == null ? null : new StackListResult<>(received, received.getCount());
    }

    @Override
    public long getStored() {
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
    public long getCacheDelta(long storedPreInsertion, long size, long remainder) {
        if (getAccessType() == AccessType.INSERT) {
            return 0;
        }

        return remainder < 1 ? size : (size - remainder);
    }
}
