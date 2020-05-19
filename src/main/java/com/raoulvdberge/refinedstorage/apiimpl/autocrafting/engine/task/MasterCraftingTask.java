package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

public class MasterCraftingTask {

    private static final String NBT_QUANTITY = "Quantity";

    private final List<Task> tasks = new ObjectArrayList<>();

    public MasterCraftingTask(INetwork network, ICraftingRequestInfo requested, int quantity,
                              ICraftingPattern pattern) {

    }

    public MasterCraftingTask(INetwork network, NBTTagCompound tag) {

    }
}
