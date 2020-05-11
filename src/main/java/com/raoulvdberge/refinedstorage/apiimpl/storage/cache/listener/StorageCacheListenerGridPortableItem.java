package com.raoulvdberge.refinedstorage.apiimpl.storage.cache.listener;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.network.MessagePortableGridItemDelta;
import com.raoulvdberge.refinedstorage.network.MessagePortableGridItemUpdate;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StorageCacheListenerGridPortableItem implements IStorageCacheListener<ItemStack> {
    private IPortableGrid portableGrid;
    private EntityPlayerMP player;

    public StorageCacheListenerGridPortableItem(IPortableGrid portableGrid, EntityPlayerMP player) {
        this.portableGrid = portableGrid;
        this.player = player;
    }

    @Override
    public void onAttached() {
        RS.INSTANCE.network.sendTo(new MessagePortableGridItemUpdate(portableGrid), player);
    }

    @Override
    public void onInvalidated() {
        // NO OP
    }

    @Override
    public void onChanged(StackListResult<ItemStack> delta) {
        List<StackListResult<ItemStack>> deltas = new ArrayList<>();

        deltas.add(delta);

        onChangedBulk(deltas);
    }

    @Override
    public void onChangedBulk(List<StackListResult<ItemStack>> storageCacheDeltas) {
        RS.INSTANCE.network.sendTo(new MessagePortableGridItemDelta(portableGrid, storageCacheDeltas), player);
    }
}
