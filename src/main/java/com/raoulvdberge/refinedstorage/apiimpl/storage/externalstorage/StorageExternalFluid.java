package com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage;

import com.cjm721.overloaded.storage.LongFluidStack;
import com.cjm721.overloaded.storage.fluid.LongFluidStorage;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.integration.overloaded.IntegrationOverloaded;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class StorageExternalFluid implements IStorageExternal<FluidStack> {
    private final IExternalStorageContext context;
    private final Supplier<IFluidHandler> handlerSupplier;
    private final boolean connectedToInterface;
    private final ExternalStorageCacheFluid cache = new ExternalStorageCacheFluid();

    public StorageExternalFluid(IExternalStorageContext context, Supplier<IFluidHandler> handlerSupplier, boolean connectedToInterface) {
        this.context = context;
        this.handlerSupplier = handlerSupplier;
        this.connectedToInterface = connectedToInterface;
    }

    public boolean isConnectedToInterface() {
        return connectedToInterface;
    }

    @Nullable
    private IFluidTankProperties[] getProperties() {
        IFluidHandler handler = handlerSupplier.get();

        return (handler != null && handler.getTankProperties() != null && handler.getTankProperties().length != 0) ? handler.getTankProperties() : null;
    }

    @Override
    public void update(INetwork network) {
        if (getAccessType() == AccessType.INSERT) {
            return;
        }

        cache.update(network, handlerSupplier.get(), (List<StackListEntry<FluidStack>>) getEntries());
    }

    @Override
    public long getCapacity() {
        IFluidTankProperties[] props = getProperties();

        if (props != null) {
            long capacity = 0;

            for (IFluidTankProperties properties : props) {
                capacity += properties.getCapacity();
            }

            return capacity;
        }

        return 0;
    }

    @Override
    public Collection<StackListEntry<FluidStack>> getEntries() {
        List<StackListEntry<FluidStack>> list = new ArrayList<>();

        IFluidHandler fluidHandler = handlerSupplier.get();
        if (IntegrationOverloaded.isLoaded() && fluidHandler instanceof LongFluidStorage) {
            LongFluidStack longFluidStack = ((LongFluidStorage) fluidHandler).getFluidStack();
            if (longFluidStack.fluidStack != null) {
                list.add(new StackListEntry<>(longFluidStack.fluidStack, longFluidStack.getAmount()));
            } else {
                list.add(new StackListEntry<>(null, 0));
            }
            return list;
        }

        IFluidTankProperties[] props = getProperties();

        if (props != null) {
            for (IFluidTankProperties properties : props) {
                FluidStack stack = properties.getContents();

                if (stack != null) {
                    list.add(new StackListEntry<>(stack, stack.amount));
                } else {
                    list.add(new StackListEntry<>(null, 0));
                }
            }

            return list;
        }

        return Collections.emptyList();
    }

    @Nullable
    @Override
    public StackListResult<FluidStack> insert(@Nonnull FluidStack stack, long size, Action action) {
        if (context.acceptsFluid(stack)) {
            IFluidHandler handler = handlerSupplier.get();

            if (handler == null)
                return new StackListResult<>(stack.copy(), size);

            while (size > 1) {
                int filled = handler.fill(
                        StackUtils.copy(stack, size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size),
                        action == Action.PERFORM);
                size -= filled;
                if (filled != Integer.MAX_VALUE)
                    break;
            }

            if (size < 1) {
                return null;
            }
        }

        return new StackListResult<>(stack.copy(), size);
    }

    @Nullable
    @Override
    public StackListResult<FluidStack> extract(@Nonnull FluidStack stack, long size, int flags, Action action) {
        IFluidHandler handler = handlerSupplier.get();

        if (handler == null) {
            return null;
        }

        long extracted = 0;
        while (size > 1) {
            FluidStack drained = handler.drain(
                    StackUtils.copy(stack, size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size),
                    action == Action.PERFORM);

            if (drained == null)
                break;
            extracted += drained.amount;

            size -= drained.amount;
            if (drained.amount != Integer.MAX_VALUE)
                break;
        }

        return new StackListResult<>(stack.copy(), extracted);
    }

    @Override
    public long getStored() {
        IFluidTankProperties[] props = getProperties();

        if (props != null) {
            int stored = 0;

            for (IFluidTankProperties properties : props) {
                FluidStack contents = properties.getContents();

                if (contents != null) {
                    stored += contents.amount;
                }
            }

            return stored;
        }

        return 0;
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
