package com.raoulvdberge.refinedstorage.apiimpl.storage.tracker;

import com.raoulvdberge.refinedstorage.api.storage.tracker.IStorageTracker;
import com.raoulvdberge.refinedstorage.api.storage.tracker.StorageTrackerEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.HashMap;
import java.util.Map;

public class StorageTrackerItem implements IStorageTracker<ItemStack> {
    private static final String NBT_STACK = "Stack";
    private static final String NBT_NAME = "Name";
    private static final String NBT_TIME = "Time";

    private final Map<Key, StorageTrackerEntry> changes = new HashMap<>();

    private final Runnable listener;

    public StorageTrackerItem(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public void changed(EntityPlayer player, ItemStack stack) {
        changes.put(new Key(stack), new StorageTrackerEntry(System.currentTimeMillis(), player.getName()));

        listener.run();
    }

    @Override
    public StorageTrackerEntry get(ItemStack stack) {
        return changes.get(new Key(stack));
    }

    public void readFromNbt(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound tag = list.getCompoundTagAt(i);

            ItemStack stack = StackUtils.deserializeStackFromNbt(tag.getCompoundTag(NBT_STACK));

            if (!stack.isEmpty()) {
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
            tag.setTag(NBT_STACK, StackUtils.serializeStackToNbt(entry.getKey().stack));

            list.appendTag(tag);
        }

        return list;
    }

    private static class Key {
        private final ItemStack stack;

        public Key(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && API.instance().getComparer().isEqualNoQuantity(stack, ((Key) other).stack);
        }

        @Override
        public int hashCode() {
            return API.instance().getItemStackHashCode(stack);
        }
    }
}
