package com.raoulvdberge.refinedstorage.apiimpl.storage.disk;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory.StorageDiskFactoryFluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.stream.Collectors;

public class StorageDiskFluid implements IStorageDisk<FluidStack> {
    public static final String NBT_VERSION = "Version";
    public static final String NBT_CAPACITY = "Capacity";
    public static final String NBT_FLUIDS = "Fluids";
    public static final String NBT_REAL_SIZE = "RealSize";

    private final World world;
    private final long capacity;

    /**
     * tracks the stored amount
     */
    private long stored;

    private final Multimap<Fluid, StackListEntry<FluidStack>> stacks = ArrayListMultimap.create();

    @Nullable
    private IStorageDiskListener listener;
    private IStorageDiskContainerContext context;

    public StorageDiskFluid(@Nullable World world, long capacity) {
        this.world = world;
        this.capacity = capacity;
    }

    @Override
    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList list = new NBTTagList();

        for (StackListEntry<FluidStack> entry : stacks.values()) {
            NBTTagCompound stackTag = new NBTTagCompound();
            entry.getStack().writeToNBT(stackTag);
            stackTag.setLong(NBT_REAL_SIZE, entry.getCount());

            list.appendTag(stackTag);
        }

        tag.setString(NBT_VERSION, RS.VERSION);
        tag.setTag(NBT_FLUIDS, list);
        tag.setLong(NBT_CAPACITY, capacity);

        return tag;
    }

    @Override
    public Collection<FluidStack> getStacks() {
        return stacks.values().stream().map(StackListEntry::getStack).collect(Collectors.toList());
    }

    @Override
    public Collection<StackListEntry<FluidStack>> getEntries() {
        return stacks.values();
    }

    @Override
    @Nullable
    public StackListResult<FluidStack> insert(@Nonnull FluidStack stack, long size, Action action) {
        for (StackListEntry<FluidStack> otherEntry : stacks.get(stack.getFluid())) {
            if (otherEntry.getStack().isFluidEqual(stack)) {
                if (getCapacity() != -1 && getStored() + size > getCapacity()) {
                    long remainingSpace = getCapacity() - getStored();

                    if (remainingSpace <= 0) {
                        return new StackListResult<>(stack.copy(), null, size);
                    }

                    if (action == Action.PERFORM) {
                        otherEntry.grow(remainingSpace);
                        stored += remainingSpace;

                        onChanged();
                    }

                    return new StackListResult<>(otherEntry.getStack().copy(), null, size - remainingSpace);
                } else {
                    if (action == Action.PERFORM) {
                        otherEntry.grow(size);
                        stored += size;

                        onChanged();
                    }

                    return null;
                }
            }
        }

        if (getCapacity() != -1 && getStored() + size > getCapacity()) {
            long remainingSpace = getCapacity() - getStored();

            if (remainingSpace <= 0) {
                return new StackListResult<>(stack.copy(), null, size);
            }

            if (action == Action.PERFORM) {
                stacks.put(stack.getFluid(), new StackListEntry<>(stack.copy(), remainingSpace));
                stored += remainingSpace;

                onChanged();
            }

            return new StackListResult<>(stack.copy(), null, size - remainingSpace);
        } else {
            if (action == Action.PERFORM) {
                stacks.put(stack.getFluid(), new StackListEntry<>(stack.copy(), size));
                stored += size;

                onChanged();
            }

            return null;
        }
    }

    @Override
    @Nullable
    public StackListResult<FluidStack> extract(@Nonnull FluidStack stack, long size, int flags, Action action) {
        for (StackListEntry<FluidStack> otherEntry : stacks.get(stack.getFluid())) {
            if (API.instance().getComparer().isEqual(otherEntry.getStack(), stack, flags)) {
                if (size > otherEntry.getCount()) {
                    size = otherEntry.getCount();
                }

                if (action == Action.PERFORM) {
                    if (otherEntry.getCount() - size == 0) {
                        stacks.remove(otherEntry.getStack().getFluid(), otherEntry);
                        stored -= otherEntry.getCount();
                    } else {
                        otherEntry.shrink(size);
                        stored -= size;
                    }

                    onChanged();
                }

                return new StackListResult<>(otherEntry.getStack().copy(), null, size);
            }
        }

        return null;
    }

    @Override
    public long getStored() {
        return this.stored;
    }

    /**
     * forces the stored amount to be re-calculated
     */
    public void calculateStoredAmount() {
        this.stored = stacks.values().stream().mapToLong(StackListEntry::getCount).sum();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public AccessType getAccessType() {
        return context.getAccessType();
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public long getCacheDelta(long storedPreInsertion, long size, long remainder) {
        if (getAccessType() == AccessType.INSERT) {
            return 0;
        }

        return remainder < 1 ? size : (size - remainder);
    }

    @Override
    public void setSettings(@Nullable IStorageDiskListener listener, IStorageDiskContainerContext context) {
        this.listener = listener;
        this.context = context;
    }

    @Override
    public String getId() {
        return StorageDiskFactoryFluid.ID;
    }

    public Multimap<Fluid, StackListEntry<FluidStack>> getRawStacks() {
        return stacks;
    }

    private void onChanged() {
        if (listener != null)
            listener.onChanged();

        if(world != null)
            API.instance().getStorageDiskManager(world).markForSaving();
    }
}
