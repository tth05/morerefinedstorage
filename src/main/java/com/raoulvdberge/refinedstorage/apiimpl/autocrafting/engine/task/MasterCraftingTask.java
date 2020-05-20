package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementFluidStack;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MasterCraftingTask {

    private final List<Task> tasks = new ObjectArrayList<>();
    private final List<ItemStack> missingItemStacks = NonNullList.create();
    private final List<FluidStack> missingFluidStacks = NonNullList.create();

    private final List<ItemStack> totalRemainder = NonNullList.withSize(20, ItemStack.EMPTY);

    private final INetwork network;

    private final ICraftingRequestInfo info;

    private final UUID id = UUID.randomUUID();

    public MasterCraftingTask(INetwork network, ICraftingRequestInfo requested, int quantity,
                              ICraftingPattern pattern) {
        this.network = network;
        this.info = requested;

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
        Task rootTask = tasks.get(0);
        CalculationResult result = rootTask.calculate(network);
        this.tasks.addAll(result.getNewTasks());

        this.missingItemStacks.addAll(result.getMissingItemStacks());
        this.missingFluidStacks.addAll(result.getMissingFluidStacks());

        return null;
    }

    public boolean hasMissing() {
        return !missingItemStacks.isEmpty() || !missingFluidStacks.isEmpty();
    }

    ////TODO:--------------------------------------------------------------------
    //TODO: Preview screen should start the task the preview was requested for and not create a new one!!!!!!!!
    ////TODO:--------------------------------------------------------------------


    public List<ICraftingPreviewElement> getPreviewStacks() {
        //Missing
        List<ICraftingPreviewElement> elements = new ArrayList<>(50);
        missingItemStacks.forEach(itemStack -> {
            CraftingPreviewElementItemStack element = new CraftingPreviewElementItemStack(itemStack);
            element.setMissing(true);
            //craft means missing...
            element.addToCraft(itemStack.getCount());
            elements.add(element);
        });

        missingFluidStacks.forEach(fluidStack -> {
            CraftingPreviewElementFluidStack element = new CraftingPreviewElementFluidStack(fluidStack);
            element.setMissing(true);
            //craft means missing...
            element.addToCraft(fluidStack.amount);
            elements.add(element);
        });

        //Available
        for (Task task : tasks) {
            for (Task.Input input : task.getInputs()) {
                boolean merged = false;

                for (ICraftingPreviewElement<?> element : elements) {
                    if (input.isFluid() && element instanceof CraftingPreviewElementFluidStack) {
                        if (FluidStack.areFluidStackTagsEqual(input.getFluidStack(),
                                ((CraftingPreviewElementFluidStack) element).getElement())) {
                            ((CraftingPreviewElementFluidStack) element)
                                    .addAvailable((int) input.getTotalInputAmount());
                            merged = true;
                            break;
                        }
                    } else if (!input.isFluid() && element instanceof CraftingPreviewElementItemStack) {
                        if (API.instance().getComparer().isEqualNoQuantity(input.getItemStacks().get(0),
                                ((CraftingPreviewElementItemStack) element).getElement())) {
                            ((CraftingPreviewElementItemStack) element).addAvailable((int) input.getTotalInputAmount());
                            merged = true;
                            break;
                        }
                    }
                }

                if(!merged) {
                    if(input.isFluid()) {
                        elements.add(new CraftingPreviewElementFluidStack(input.getFluidStack(),
                                (int) input.getTotalInputAmount(), false, 0));
                    } else {
                        elements.add(new CraftingPreviewElementItemStack(input.getItemStacks().get(0),
                                (int) input.getTotalInputAmount(), false, 0));
                    }
                }
            }
        }

        return elements;
    }

    public ICraftingRequestInfo getRequested() {
        return info;
    }

    public UUID getId() {
        return this.id;
    }

    public ICraftingPattern getPattern() {
        return this.tasks.get(0).getPattern();
    }
}
