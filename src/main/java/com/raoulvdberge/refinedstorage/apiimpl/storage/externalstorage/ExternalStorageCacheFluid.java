package com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage;

import com.cjm721.overloaded.storage.LongFluidStack;
import com.cjm721.overloaded.storage.fluid.LongFluidStorage;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.integration.overloaded.IntegrationOverloaded;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ExternalStorageCacheFluid {
    private List<StackListEntry<FluidStack>> cache;

    public void update(INetwork network, @Nullable IFluidHandler handler, List<StackListEntry<FluidStack>> entries) {
        if (handler == null) {
            return;
        }

        if (cache == null) {
            cache = new ArrayList<>();

            for (StackListEntry<FluidStack> entry : entries) {
                FluidStack stack = entry.getStack();
                if (stack != null) {
                    cache.add(new StackListEntry<>(entry.getStack().copy(), entry.getCount()));
                }
            }

            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            StackListEntry<FluidStack> actual = entries.get(i);
            FluidStack actualStack = actual.getStack();

            if (i >= cache.size()) { // ENLARGED
                if (actualStack != null) {
                    network.getFluidStorageCache().add(actualStack, actual.getCount(), true);

                    cache.add(new StackListEntry<>(actualStack.copy(), actual.getCount()));
                }

                continue;
            }

            StackListEntry<FluidStack> cached = cache.get(i);
            FluidStack cachedStack = cached.getStack();

            if (actualStack == null && cachedStack == null) { // NONE
                continue;
            }

            if (actualStack == null) { // REMOVED
                network.getFluidStorageCache().remove(cachedStack, cached.getCount(), true);

                cache.set(i, null);
            } else if (cachedStack == null) { // ADDED
                network.getFluidStorageCache().add(actualStack, actual.getCount(), true);

                cache.set(i, new StackListEntry<>(actualStack.copy(), actual.getCount()));
            } else if (!API.instance().getComparer().isEqual(actualStack, cachedStack, IComparer.COMPARE_NBT)) { // CHANGED
                network.getFluidStorageCache().remove(cachedStack, cached.getCount(), true);
                network.getFluidStorageCache().add(actualStack, actual.getCount(), true);

                cache.set(i, new StackListEntry<>(actualStack.copy(), actual.getCount()));
            } else if (actual.getCount() > cached.getCount()) { // COUNT_CHANGED
                network.getFluidStorageCache().add(actualStack, actual.getCount() - cached.getCount(), true);

                cached.setCount(actual.getCount());
            } else if (actual.getCount() < cached.getCount()) { // COUNT_CHANGED
                network.getFluidStorageCache().remove(actualStack, cached.getCount() - actual.getCount(), true);

                cached.setCount(actual.getCount());
            }
        }

        if (cache.size() > entries.size()) { // SHRUNK
            for (int i = cache.size() - 1; i >= handler.getTankProperties().length; --i) { // Reverse order for the remove call.
                StackListEntry<FluidStack> cached = cache.get(i);
                FluidStack cachedStack = cached.getStack();

                if (cachedStack != null) {
                    network.getFluidStorageCache().remove(cachedStack, cached.getCount(), true);
                }

                cache.remove(i);
            }
        }

        network.getFluidStorageCache().flush();
    }
}
