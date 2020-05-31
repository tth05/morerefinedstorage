package com.raoulvdberge.refinedstorage.apiimpl.storage.tracker;

import com.raoulvdberge.refinedstorage.api.storage.tracker.IStorageTracker;
import com.raoulvdberge.refinedstorage.api.storage.tracker.StorageTrackerEntry;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

public class StorageTrackerFluid implements IStorageTracker<FluidStack> {
    private static final String NBT_STACK = "Stack";
    private static final String NBT_NAME = "Name";
    private static final String NBT_TIME = "Time";

    private final Map<Key, StorageTrackerEntry> changes = new HashMap<>();
    private final Runnable listener;

    public StorageTrackerFluid(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public void changed(EntityPlayer player, FluidStack stack) {
        changes.put(new Key(stack), new StorageTrackerEntry(System.currentTimeMillis(), player.getName()));

        listener.run();
    }

    @Override
    public StorageTrackerEntry get(FluidStack stack) {
        return changes.get(new Key(stack));
    }

    public void readFromNbt(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound tag = list.getCompoundTagAt(i);

            FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(NBT_STACK));

            if (stack != null) {
                changes.put(new Key(stack), new StorageTrackerEntry(tag.getLong(NBT_TIME), tag.getString(NBT_NAME)));
            }
        }
    }

    public NBTTagList serializeNbt() {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<Key, StorageTrackerEntry> entry : changes.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();

            tag.setLong(NBT_TIME, entry.getValue().getTime());
            tag.setString(NBT_NAME, entry.getValue().getName());
            tag.setTag(NBT_STACK, entry.getKey().stack.writeToNBT(new NBTTagCompound()));

            list.appendTag(tag);
        }

        return list;
    }

    private static class Key {
        private final FluidStack stack;

        public Key(FluidStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && API.instance().getComparer().isEqual(stack, ((Key) other).stack, IComparer.COMPARE_NBT);
        }

        @Override
        public int hashCode() {
            return API.instance().getFluidStackHashCode(stack);
        }
    }
}
