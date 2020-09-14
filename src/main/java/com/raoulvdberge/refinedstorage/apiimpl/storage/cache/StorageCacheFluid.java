package com.raoulvdberge.refinedstorage.apiimpl.storage.cache;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.*;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class StorageCacheFluid implements IStorageCache<FluidStack> {
    public static final Consumer<INetwork> INVALIDATE = n -> n.getFluidStorageCache().invalidate();

    private final INetwork network;
    private final CopyOnWriteArrayList<IStorage<FluidStack>> storages = new CopyOnWriteArrayList<>();
    private final IStackList<FluidStack> list = API.instance().createFluidStackList();
    private final IStackList<FluidStack> craftables = API.instance().createFluidStackList();
    private final List<IStorageCacheListener<FluidStack>> listeners = new LinkedList<>();
    private final List<StackListResult<FluidStack>> batchedChanges = new ArrayList<>();

    public StorageCacheFluid(INetwork network) {
        this.network = network;
    }

    @Override
    public synchronized void invalidate() {
        storages.clear();

        network.getNodeGraph().all().stream()
            .filter(node -> node.canUpdate() && node instanceof IStorageProvider)
            .forEach(node -> ((IStorageProvider) node).addFluidStorages(storages));

        list.clear();

        sort();

        for (IStorage<FluidStack> storage : storages) {
            if (storage.getAccessType() == AccessType.INSERT) {
                continue;
            }

            for (StackListEntry<FluidStack> stack : storage.getEntries()) {
                if(stack != null && stack.getCount() > 0)
                    add(stack.getStack(), stack.getCount(), true, false);
            }
        }

        listeners.forEach(IStorageCacheListener::onInvalidated);
    }

    @Override
    public synchronized void add(@Nonnull FluidStack stack, long size, boolean rebuilding, boolean batched) {
        StackListResult<FluidStack> result = list.add(stack, size);

        if (!rebuilding) {
            if (!batched) {
                listeners.forEach(l -> l.onChanged(result));
            } else {
                batchedChanges.add(result);
            }
        }
    }

    @Override
    public synchronized void remove(@Nonnull FluidStack stack, long size, boolean batched) {
        StackListResult<FluidStack> result = list.remove(stack, size);

        if (result != null) {
            if (!batched) {
                listeners.forEach(l -> l.onChanged(result));
            } else {
                batchedChanges.add(result);
            }
        }
    }

    @Override
    public synchronized void flush() {
        if (!batchedChanges.isEmpty()) {
            if (batchedChanges.size() > 1) {
                listeners.forEach(l -> l.onChangedBulk(batchedChanges));
            } else {
                batchedChanges.forEach(change -> listeners.forEach(l -> l.onChanged(change)));
            }

            batchedChanges.clear();
        }
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
        storages.sort(IStorage.COMPARATOR);
    }

    @Override
    public IStackList<FluidStack> getList() {
        return list;
    }

    @Override
    public IStackList<FluidStack> getCraftablesList() {
        return craftables;
    }

    @Override
    public List<IStorage<FluidStack>> getStorages() {
        return storages;
    }
}
