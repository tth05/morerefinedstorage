package com.raoulvdberge.refinedstorage.apiimpl.storage.disk.factory;

import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskFactory;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.StorageDiskFluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

public class StorageDiskFactoryFluid implements IStorageDiskFactory<FluidStack> {
    public static final String ID = "normal_fluid";

    @Override
    public IStorageDisk<FluidStack> createFromNbt(World world, NBTTagCompound tag) {
        StorageDiskFluid disk = new StorageDiskFluid(world, tag.getInteger(StorageDiskFluid.NBT_CAPACITY));

        NBTTagList list = (NBTTagList) tag.getTag(StorageDiskFluid.NBT_FLUIDS);

        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound stackTag = list.getCompoundTagAt(i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(stackTag);
            long realCount = 0;
            if(!stackTag.hasKey(StorageDiskFluid.NBT_REAL_SIZE))
                realCount = stack.amount;
            else
                realCount = stackTag.getLong(StorageDiskFluid.NBT_REAL_SIZE);

            if (stack != null) {
                disk.getRawStacks().put(stack.getFluid(), new StackListEntry<>(stack, realCount));
            }
        }

        disk.calculateStoredAmount();

        return disk;
    }

    @Override
    public IStorageDisk<FluidStack> create(World world, int capacity) {
        return new StorageDiskFluid(world, capacity);
    }
}
