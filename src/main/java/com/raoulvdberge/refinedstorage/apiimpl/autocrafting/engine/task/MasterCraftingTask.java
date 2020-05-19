package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class MasterCraftingTask {

    private final List<Task> tasks = new ObjectArrayList<>();
    private final List<ItemStack> missingItemStacks = NonNullList.create();
    private final List<ItemStack> missingFluidStacks = NonNullList.create();

    private final List<ItemStack> totalRemainder = NonNullList.withSize(20, ItemStack.EMPTY);

    private final INetwork network;

    public MasterCraftingTask(INetwork network, ICraftingRequestInfo requested, int quantity,
                              ICraftingPattern pattern) {
        this.network = network;
        if (pattern.isProcessing())
            throw new UnsupportedOperationException();
        else
            tasks.add(new CraftingTask(pattern, quantity));

        //TODO: handle byproducts
    }

    public MasterCraftingTask(INetwork network, NBTTagCompound tag) throws CraftingTaskReadException {
        this.network = network;

        throw new CraftingTaskReadException("yeet");
    }

    public void update() {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            tasks.get(i).update();
        }
    }

    public ICraftingTaskError calculate() {
        Deque<Task> taskStack = new ArrayDeque<>();
        taskStack.add(tasks.get(0));

        while (!taskStack.isEmpty()) {
            Task rootTask = taskStack.pop();


        }

        return null;
    }

    public boolean hasMissing() {
        return false;
    }
}
