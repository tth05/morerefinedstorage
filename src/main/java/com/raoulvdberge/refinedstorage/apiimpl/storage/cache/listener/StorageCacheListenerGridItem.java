package com.raoulvdberge.refinedstorage.apiimpl.storage.cache.listener;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.network.MessageGridItemDelta;
import com.raoulvdberge.refinedstorage.network.MessageGridItemUpdate;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class StorageCacheListenerGridItem implements IStorageCacheListener<ItemStack> {
    private final EntityPlayerMP player;
    private final INetwork network;

    public StorageCacheListenerGridItem(EntityPlayerMP player, INetwork network) {
        this.player = player;
        this.network = network;
    }

    @Override
    public void onAttached() {
        RS.INSTANCE.network.sendTo(new MessageGridItemUpdate(network, network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)), player);
    }

    @Override
    public void onInvalidated() {
        // NO OP
    }

    @Override
    public void onChanged(@Nonnull StackListResult<ItemStack> delta) {
        List<StackListResult<ItemStack>> deltas = new ArrayList<>();
        deltas.add(delta);

        onChangedBulk(deltas);
    }

    @Override
    public void onChangedBulk(@Nonnull List<StackListResult<ItemStack>> deltas) {
        RS.INSTANCE.network.sendTo(new MessageGridItemDelta(network, deltas), player);
    }
}
