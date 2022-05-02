package com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExternalStorageCacheItem {
    private List<StackListEntry<ItemStack>> cache;

    public void update(INetwork network, @Nullable IItemHandler handler, List<StackListEntry<ItemStack>> entries) {
        if (handler == null) {
            return;
        }

        if (cache == null) {
            cache = new ArrayList<>();

            for (StackListEntry<ItemStack> entry : entries) {
                cache.add(new StackListEntry<>(entry.getStack().copy(), entry.getCount()));
            }

            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            StackListEntry<ItemStack> actual = entries.get(i);
            ItemStack actualStack = actual.getStack();

            if (i >= cache.size()) { // ENLARGED
                if (!actualStack.isEmpty()) {
                    network.getItemStorageCache().add(actualStack, actual.getCount(), true);

                    cache.add(new StackListEntry<>(actualStack.copy(), actual.getCount()));
                }

                continue;
            }

            StackListEntry<ItemStack> cached = cache.get(i);
            ItemStack cachedStack = cached.getStack();

            if (!cachedStack.isEmpty() && actualStack.isEmpty()) { // REMOVED
                network.getItemStorageCache().remove(cachedStack, cached.getCount(), true);

                cache.set(i, new StackListEntry<>(ItemStack.EMPTY, 0));
            } else if (cachedStack.isEmpty() && !actualStack.isEmpty()) { // ADDED
                network.getItemStorageCache().add(actualStack, actual.getCount(), true);

                cache.set(i, new StackListEntry<>(actualStack.copy(), actual.getCount()));
            } else if (!API.instance().getComparer().isEqualNoQuantity(cachedStack, actualStack)) { // CHANGED
                network.getItemStorageCache().remove(cachedStack, cached.getCount(), true);
                network.getItemStorageCache().add(actualStack, actual.getCount(), true);

                cache.set(i, new StackListEntry<>(actualStack.copy(), actual.getCount()));
            } else if (cached.getCount() != actual.getCount()) { // COUNT_CHANGED
                long delta = actual.getCount() - cached.getCount();

                if (delta > 0) {
                    network.getItemStorageCache().add(actualStack, delta, true);
                } else {
                    network.getItemStorageCache().remove(actualStack, Math.abs(delta), true);
                }
                cached.grow(delta);
            }
        }

        if (cache.size() > entries.size()) { // SHRUNK
            for (int i = cache.size() - 1; i >= handler.getSlots(); --i) { // Reverse order for the remove call.
                StackListEntry<ItemStack> cached = cache.get(i);
                ItemStack cachedStack = cached.getStack();

                if (!cachedStack.isEmpty()) {
                    network.getItemStorageCache().remove(cachedStack, cached.getCount(), true);
                }

                cache.remove(i);
            }
        }

        network.getItemStorageCache().flush();
    }
}
