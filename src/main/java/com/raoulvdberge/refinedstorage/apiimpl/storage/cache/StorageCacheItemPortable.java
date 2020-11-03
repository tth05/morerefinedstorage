package com.raoulvdberge.refinedstorage.apiimpl.storage.cache;

import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StorageCacheItemPortable implements IStorageCache<ItemStack> {
    private final IPortableGrid portableGrid;
    private final IStackList<ItemStack> list = API.instance().createItemStackList();
    private final List<IStorageCacheListener<ItemStack>> listeners = new LinkedList<>();

    public StorageCacheItemPortable(IPortableGrid portableGrid) {
        this.portableGrid = portableGrid;
    }

    @Override
    public void invalidate() {
        list.clearCounts();

        if (portableGrid.getItemStorage() != null) {
            for (StackListEntry<ItemStack> entry : portableGrid.getItemStorage().getEntries()) {
                if (entry != null && !entry.getStack().isEmpty() && entry.getCount() > 0)
                    list.add(entry.getStack(), entry.getCount());
            }
        }

        list.clearEmpty();

        listeners.forEach(IStorageCacheListener::onInvalidated);
    }

    @Override
    public void add(@Nonnull ItemStack stack, long size, boolean batched) {
        StackListResult<ItemStack> result = list.add(stack, size);

        listeners.forEach(l -> l.onChanged(result));
    }

    @Override
    public void remove(@Nonnull ItemStack stack, long size, boolean batched) {
        StackListResult<ItemStack> result = list.remove(stack, size);

        if (result != null) {
            listeners.forEach(l -> l.onChanged(result));
        }
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException("Cannot flush portable grid storage cache");
    }

    @Override
    public void addListener(IStorageCacheListener<ItemStack> listener) {
        listeners.add(listener);

        listener.onAttached();
    }

    @Override
    public void removeListener(IStorageCacheListener<ItemStack> listener) {
        listeners.remove(listener);
    }

    @Override
    public void reAttachListeners() {
        listeners.forEach(IStorageCacheListener::onAttached);
    }

    @Override
    public void sort() {
        // NO OP
    }

    @Override
    public IStackList<ItemStack> getList() {
        return list;
    }

    @Override
    public IStackList<ItemStack> getCraftablesList() {
        throw new RuntimeException("Unsupported");
    }

    @Override
    public List<IStorage<ItemStack>> getStorages() {
        return Collections.emptyList();
    }
}
