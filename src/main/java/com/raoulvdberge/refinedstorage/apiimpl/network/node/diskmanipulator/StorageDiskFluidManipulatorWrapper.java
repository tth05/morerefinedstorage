package com.raoulvdberge.refinedstorage.apiimpl.network.node.diskmanipulator;

import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsDisk;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public class StorageDiskFluidManipulatorWrapper implements IStorageDisk<FluidStack> {
    private final NetworkNodeDiskManipulator diskManipulator;
    private final IStorageDisk<FluidStack> parent;
    private int lastState;

    public StorageDiskFluidManipulatorWrapper(NetworkNodeDiskManipulator diskManipulator, IStorageDisk<FluidStack> parent) {
        this.diskManipulator = diskManipulator;
        this.parent = parent;
        this.setSettings(
            () -> {
                int currentState = ConstantsDisk.getDiskState(getStored(), getCapacity());

                if (lastState != currentState) {
                    lastState = currentState;

                    WorldUtils.updateBlock(diskManipulator.getNetworkNodeWorld(), diskManipulator.getNetworkNodePos());
                }
            },
            diskManipulator
        );
        this.lastState = ConstantsDisk.getDiskState(getStored(), getCapacity());
    }

    @Override
    public long getCapacity() {
        return parent.getCapacity();
    }

    @Override
    public void setSettings(@Nullable IStorageDiskListener listener, IStorageDiskContainerContext context) {
        parent.setSettings(listener, context);
    }

    @Override
    public NBTTagCompound writeToNbt() {
        return parent.writeToNbt();
    }

    @Override
    public String getId() {
        return parent.getId();
    }

    @Override
    public Collection<StackListEntry<FluidStack>> getEntries() {
        return parent.getEntries();
    }

    @Override
    @Nullable
    public StackListResult<FluidStack> insert(@Nonnull FluidStack stack, long size, Action action) {
        if (!diskManipulator.getConfig().acceptsFluid(stack)) {
            return new StackListResult<>(stack.copy(), size);
        }

        return parent.insert(stack, size, action);
    }

    @Override
    @Nullable
    public StackListResult<FluidStack> extract(@Nonnull FluidStack stack, long size, int flags, Action action) {
        if (!diskManipulator.getConfig().acceptsFluid(stack)) {
            return null;
        }

        return parent.extract(stack, size, flags, action);
    }

    @Override
    public long getStored() {
        return parent.getStored();
    }

    @Override
    public int getPriority() {
        return parent.getPriority();
    }

    @Override
    public AccessType getAccessType() {
        return parent.getAccessType();
    }

    @Override
    public long getCacheDelta(long storedPreInsertion, long size, long remainder) {
        return parent.getCacheDelta(storedPreInsertion, size, remainder);
    }
}
