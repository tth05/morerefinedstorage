package com.raoulvdberge.refinedstorage.apiimpl.storage.disk;

import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public class StorageDiskItemPortable implements IStorageDisk<ItemStack> {
    private final IStorageDisk<ItemStack> parent;
    private final IPortableGrid portableGrid;

    public StorageDiskItemPortable(IStorageDisk<ItemStack> parent, IPortableGrid portableGrid) {
        this.parent = parent;
        this.portableGrid = portableGrid;
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
    public Collection<StackListEntry<ItemStack>> getEntries() {
        return parent.getEntries();
    }

    @Nullable
    @Override
    public StackListResult<ItemStack> insert(@Nonnull ItemStack stack, long size, Action action) {
        long storedPre = parent.getStored();

        StackListResult<ItemStack> remainder = parent.insert(stack, size, action);

        if (action == Action.PERFORM) {
            long inserted = parent.getCacheDelta(storedPre, size, remainder == null ? 0 : remainder.getCount());

            if (inserted > 0) {
                portableGrid.getItemCache().add(stack, inserted, false);
            }
        }

        return remainder;
    }

    @Nullable
    @Override
    public StackListResult<ItemStack> extract(@Nonnull ItemStack stack, long size, int flags, Action action) {
        StackListResult<ItemStack> extracted = parent.extract(stack, size, flags, action);

        if (action == Action.PERFORM && extracted != null) {
            portableGrid.getItemCache().remove(extracted.getStack(), extracted.getCount(), false);
        }

        return extracted;
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

    @Override
    public String getId() {
        return parent.getId();
    }
}
