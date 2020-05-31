package com.raoulvdberge.refinedstorage.apiimpl.storage.cache.listener;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.network.MessageGridFluidDelta;
import com.raoulvdberge.refinedstorage.network.MessageGridFluidUpdate;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class StorageCacheListenerGridFluid implements IStorageCacheListener<FluidStack> {
    private final EntityPlayerMP player;
    private final INetwork network;

    public StorageCacheListenerGridFluid(EntityPlayerMP player, INetwork network) {
        this.player = player;
        this.network = network;
    }

    @Override
    public void onAttached() {
        RS.INSTANCE.network.sendTo(new MessageGridFluidUpdate(network, network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)), player);
    }

    @Override
    public void onInvalidated() {
        // NO OP
    }

    @Override
    public void onChanged(@Nonnull StackListResult<FluidStack> delta) {
        List<StackListResult<FluidStack>> deltas = new ArrayList<>();
        deltas.add(delta);

        onChangedBulk(deltas);
    }

    @Override
    public void onChangedBulk(@Nonnull List<StackListResult<FluidStack>> deltas) {
        RS.INSTANCE.network.sendTo(new MessageGridFluidDelta(network, deltas), player);
    }
}
