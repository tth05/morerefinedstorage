package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.registry;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.registry.ICraftingTaskFactory;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.MasterCraftingTask;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public class CraftingTaskFactory implements ICraftingTaskFactory {
    public static final String ID = "v8";

    @Nonnull
    @Override
    public MasterCraftingTask create(INetwork network, ICraftingRequestInfo requested, ICraftingPattern pattern) {
        return new MasterCraftingTask(network, requested, pattern);
    }

    @Override
    public MasterCraftingTask createFromNbt(INetwork network, NBTTagCompound tag) throws CraftingTaskReadException {
        return new MasterCraftingTask(network, tag);
    }
}
