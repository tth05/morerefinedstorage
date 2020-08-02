package com.raoulvdberge.refinedstorage.apiimpl.network.node.diskdrive;

import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsDisk;
import com.raoulvdberge.refinedstorage.tile.config.IFilterable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public class StorageDiskItemDriveWrapper implements IStorageDisk<ItemStack> {
    private final NetworkNodeDiskDrive diskDrive;
    private final IStorageDisk<ItemStack> parent;
    private int lastState;

    public StorageDiskItemDriveWrapper(NetworkNodeDiskDrive diskDrive, IStorageDisk<ItemStack> parent) {
        this.diskDrive = diskDrive;
        this.parent = parent;
        this.setSettings(
            () -> {
                int currentState = ConstantsDisk.getDiskState(getStored(), getCapacity());

                if (this.lastState != currentState) {
                    this.lastState = currentState;

                    diskDrive.requestBlockUpdate();
                }
            },
            diskDrive
        );
        this.lastState = ConstantsDisk.getDiskState(getStored(), getCapacity());
    }

    @Override
    public int getPriority() {
        return diskDrive.getPriority();
    }

    @Override
    public AccessType getAccessType() {
        return parent.getAccessType();
    }

    @Override
    public Collection<ItemStack> getStacks() {
        return parent.getStacks();
    }

    @Override
    public Collection<StackListEntry<ItemStack>> getEntries() {
        return parent.getEntries();
    }

    @Override
    @Nullable
    public StackListResult<ItemStack> insert(@Nonnull ItemStack stack, long size, Action action) {
        if (!IFilterable.acceptsItem(diskDrive.getItemFilters(), diskDrive.getMode(), diskDrive.getCompare(), stack)) {
            return new StackListResult<>(stack.copy(), null, size);
        }

        return parent.insert(stack, size, action);
    }

    @Nullable
    @Override
    public StackListResult<ItemStack> extract(@Nonnull ItemStack stack, long size, int flags, Action action) {
        return parent.extract(stack, size, flags, action);
    }

    @Override
    public long getStored() {
        return parent.getStored();
    }

    @Override
    public long getCacheDelta(long storedPreInsertion, long size, long remainder) {
        return parent.getCacheDelta(storedPreInsertion, size, remainder);
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
}
