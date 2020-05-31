package com.raoulvdberge.refinedstorage.apiimpl.storage.cache.listener;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.network.MessagePortableGridFluidDelta;
import com.raoulvdberge.refinedstorage.network.MessagePortableGridFluidUpdate;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class StorageCacheListenerGridPortableFluid implements IStorageCacheListener<FluidStack> {
    private final IPortableGrid portableGrid;
    private final EntityPlayerMP player;

    public StorageCacheListenerGridPortableFluid(IPortableGrid portableGrid, EntityPlayerMP player) {
        this.portableGrid = portableGrid;
        this.player = player;
    }

    @Override
    public void onAttached() {
        RS.INSTANCE.network.sendTo(new MessagePortableGridFluidUpdate(portableGrid), player);
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
    public void onChangedBulk(@Nonnull List<StackListResult<FluidStack>> storageCacheDeltas) {
        RS.INSTANCE.network.sendTo(new MessagePortableGridFluidDelta(portableGrid, storageCacheDeltas), player);
    }
}
