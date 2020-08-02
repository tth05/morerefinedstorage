package com.raoulvdberge.refinedstorage.apiimpl.storage.cache;

import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StorageCacheFluidPortable implements IStorageCache<FluidStack> {
    private final IPortableGrid portableGrid;
    private final IStackList<FluidStack> list = API.instance().createFluidStackList();
    private final List<IStorageCacheListener<FluidStack>> listeners = new LinkedList<>();


    public StorageCacheFluidPortable(IPortableGrid portableGrid) {
        this.portableGrid = portableGrid;
    }

    @Override
    public void invalidate() {
        list.clear();

        if (portableGrid.getFluidStorage() != null) {
            for (StackListEntry<FluidStack> e : portableGrid.getFluidStorage().getEntries()) {
                if(e != null && e.getCount() > 0)
                    list.add(e.getStack(), e.getCount());
            }
        }

        listeners.forEach(IStorageCacheListener::onInvalidated);
    }

    @Override
    public void add(@Nonnull FluidStack stack, long size, boolean rebuilding, boolean batched) {
        StackListResult<FluidStack> result = list.add(stack, size);

        if (!rebuilding) {
            listeners.forEach(l -> l.onChanged(result));
        }
    }

    @Override
    public void remove(@Nonnull FluidStack stack, long size, boolean batched) {
        StackListResult<FluidStack> result = list.remove(stack, size);
        if (result != null) {
            listeners.forEach(l -> l.onChanged(result));
        }
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException("Cannot flush portable grid storage cache");
    }

    @Override
    public void addListener(IStorageCacheListener<FluidStack> listener) {
        listeners.add(listener);

        listener.onAttached();
    }

    @Override
    public void removeListener(IStorageCacheListener<FluidStack> listener) {
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
    public IStackList<FluidStack> getList() {
        return list;
    }

    @Override
    public IStackList<FluidStack> getCraftablesList() {
        throw new RuntimeException("Unsupported");
    }

    @Override
    public List<IStorage<FluidStack>> getStorages() {
        return Collections.emptyList();
    }
}
