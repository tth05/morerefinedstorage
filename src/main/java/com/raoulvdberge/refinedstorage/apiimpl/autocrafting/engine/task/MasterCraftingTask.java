package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementList;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementItemRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.DurabilityInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.InfiniteInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementFluidStack;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementItemStack;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.*;

public class MasterCraftingTask implements ICraftingTask {

    /**
     * All current tasks that make up this auto crafting task
     */
    private final List<Task> tasks = new ObjectArrayList<>();
    /**
     * Used to know how many crafting updates are left for a specific container
     */
    private final Map<ICraftingPatternContainer, Integer> updateCountMap = new Object2IntOpenHashMap<>(20);
    private IStackList<ItemStack> missingItemStacks = API.instance().createItemStackList();
    private IStackList<FluidStack> missingFluidStacks = API.instance().createFluidStackList();

    private final INetwork network;

    private final ICraftingRequestInfo info;

    private final UUID id = UUID.randomUUID();

    private final int quantity;
    /**
     * Timestamp of when the execution started
     */
    private long executionStarted = -1;
    /**
     * The time in milli seconds it took for the calculation to complete
     */
    private long calculationTime = -1;
    private long ticks = 0;
    private boolean canUpdate;

    public MasterCraftingTask(@Nonnull INetwork network, @Nonnull ICraftingRequestInfo requested, int quantity,
                              @Nonnull ICraftingPattern pattern) {
        this.network = network;
        this.info = requested;
        this.quantity = quantity;

        if (pattern.isProcessing())
            tasks.add(new ProcessingTask(pattern, quantity, requested.getFluid() != null));
        else
            tasks.add(new CraftingTask(pattern, quantity));
    }

    public MasterCraftingTask(@Nonnull INetwork network, @Nonnull NBTTagCompound tag) throws CraftingTaskReadException {
        this.network = network;

        throw new CraftingTaskReadException("yeet");
    }

    @Override
    public boolean update() {
        if (!canUpdate)
            return false;
        if (executionStarted == -1)
            executionStarted = System.currentTimeMillis();

        boolean allFinished = true;

        updateCountMap.clear();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task task = tasks.get(i);
            if (task.isFinished())
                continue;

            Set<ICraftingPatternContainer> containers = network.getCraftingManager().getAllContainer(task.getPattern());
            for (ICraftingPatternContainer container : containers) {
                if (container.getUpdateInterval() < 0)
                    throw new IllegalStateException(container + " has an update interval of < 0");

                //check if container is allowed to update
                if (ticks % container.getUpdateInterval() != 0)
                    continue;

                //get current update count
                Integer remainingUpdates = updateCountMap.get(container);
                if (remainingUpdates == null)
                    remainingUpdates = container.getMaximumSuccessfulCraftingUpdates();

                //stop if no updates are left
                if (remainingUpdates < 1)
                    continue;

                remainingUpdates -= task.update(network, container, remainingUpdates);
                if (task.isFinished())
                    break;

                //avoid put call
                if (remainingUpdates != container.getMaximumSuccessfulCraftingUpdates())
                    updateCountMap.put(container, remainingUpdates);
            }

            if (!task.isFinished())
                allFinished = false;
        }

        ticks++;
        return allFinished;
    }

    @Override
    public ICraftingTaskError calculate() {
        long calculationStarted = System.currentTimeMillis();

        Task rootTask = tasks.get(0);

        //add outputs of root task to recursed items
        IStackList<ItemStack> items = API.instance().createItemStackList();
        rootTask.getPattern().getOutputs().forEach(items::add);
        IStackList<FluidStack> fluids = API.instance().createFluidStackList();
        rootTask.getPattern().getFluidOutputs().forEach(fluids::add);

        CalculationResult result = rootTask.calculate(network,
                new ObjectArrayList<>(),
                items,
                fluids,
                calculationStarted
        );

        //instantly cancel if calculation had any error, saver than waiting for the player to cancel
        if (result.getError() != null) {
            onCancelled();
        } else {
            this.tasks.addAll(result.getNewTasks());

            this.missingItemStacks = result.getMissingItemStacks();
            this.missingFluidStacks = result.getMissingFluidStacks();

            this.calculationTime = System.currentTimeMillis() - calculationStarted;
        }

        return result.getError();
    }

    @Override
    public List<ICraftingPreviewElement> getPreviewStacks() {
        List<ICraftingPreviewElement> elements = new ArrayList<>(50);

        //Output
        if (this.info.getItem() != null)
            elements.add(new CraftingPreviewElementItemStack(this.info.getItem(), 0, false, this.quantity));
        else if (this.info.getFluid() != null)
            elements.add(new CraftingPreviewElementFluidStack(this.info.getFluid(), 0, false, this.quantity));

        //Missing
        for (StackListEntry<ItemStack> entry : this.missingItemStacks.getStacks()) {
            ItemStack itemStack = entry.getStack();

            CraftingPreviewElementItemStack element =
                    new CraftingPreviewElementItemStack(itemStack, 0, true, itemStack.getCount());
            elements.add(element);
        }

        for (StackListEntry<FluidStack> entry : this.missingFluidStacks.getStacks()) {
            FluidStack fluidStack = entry.getStack();

            CraftingPreviewElementFluidStack element =
                    new CraftingPreviewElementFluidStack(fluidStack, 0, true, fluidStack.amount);
            elements.add(element);
        }

        //Available
        for (Task task : tasks) {
            for (Input input : task.getInputs()) {
                boolean merged = false;

                //try to merge into existing
                for (ICraftingPreviewElement<?> element : elements) {
                    if (input.isFluid() && element instanceof CraftingPreviewElementFluidStack) {
                        CraftingPreviewElementFluidStack previewElement = ((CraftingPreviewElementFluidStack) element);

                        if (API.instance().getComparer().isEqual(input.getFluidStack(), previewElement.getElement(),
                                IComparer.COMPARE_NBT)) {
                            previewElement.addAvailable(input.getTotalInputAmount());
                            previewElement.addToCraft(input.getToCraftAmount());
                            merged = true;
                            break;
                        }
                    } else if (!input.isFluid() && element instanceof CraftingPreviewElementItemStack) {
                        CraftingPreviewElementItemStack previewElement = ((CraftingPreviewElementItemStack) element);
                        boolean isDurabilityInput = input instanceof DurabilityInput;

                        if (API.instance().getComparer().isEqualNoQuantity(input.getCompareableItemStack(),
                                previewElement.getElement())) {

                            //do not merge available for infinite inputs
                            if (!(input instanceof InfiniteInput) || ((InfiniteInput) input).containsItem()) {
                                previewElement.addAvailable(
                                        (isDurabilityInput ? ((DurabilityInput) input).getTotalItemInputAmount() :
                                                input.getTotalInputAmount()));
                            }
                            previewElement.addToCraft(input.getToCraftAmount());
                            merged = true;
                            break;
                        }
                    }
                }

                //if there's no existing element, create a new one
                if (!merged) {
                    if (input.isFluid()) {
                        elements.add(new CraftingPreviewElementFluidStack(
                                input.getFluidStack(),
                                input.getTotalInputAmount(), false,
                                input.getToCraftAmount()));
                    } else {
                        boolean isDurabilityInput = input instanceof DurabilityInput;

                        long available = (isDurabilityInput ? ((DurabilityInput) input).getTotalItemInputAmount() :
                                input.getTotalInputAmount());

                        //fix available count for infinite inputs
                        if (input instanceof InfiniteInput && !((InfiniteInput) input).containsItem())
                            available = 0;

                        elements.add(new CraftingPreviewElementItemStack(
                                input.getCompareableItemStack(),
                                available, false,
                                input.getToCraftAmount()));
                    }
                }
            }
        }

        return elements;
    }

    @Override
    public void onCancelled() {
        //just insert all stored items back into network
        for (Task task : this.tasks) {
            //TODO: also insert remaining items of tasks
            for (Input input : task.getInputs()) {
                boolean isDurabilityInput = input instanceof DurabilityInput;

                if (input instanceof InfiniteInput && !((InfiniteInput) input).containsItem())
                    continue;

                List<ItemStack> itemStacks = input.getItemStacks();
                //TODO: insert remainder and handle remainder if network is full
                for (int i = 0; i < itemStacks.size(); i++) {
                    ItemStack itemStack = itemStacks.get(i);
                    //TODO: real stack counts
                    int amount = isDurabilityInput ? 1 : input.getCurrentInputCounts().get(i).intValue();
                    if (amount > 0)
                        network.insertItem(itemStack, amount, Action.PERFORM);
                }

                if (input.isFluid()) {
                    //TODO: real stack amounts
                    int amount = input.getCurrentInputCounts().get(0).intValue();
                    if (amount > 0)
                        network.insertFluid(input.getFluidStack(), amount, Action.PERFORM);
                }
            }
        }
    }

    @Override
    public int onTrackedInsert(ItemStack stack, int size) {
        stack.setCount(size);

        for (int i = this.tasks.size() - 1; i >= 0; i--) {
            Task task = this.tasks.get(i);
            if (!(task instanceof ProcessingTask))
                continue;

            size = task.supplyInput(stack);
            if (size < 0)
                return 0;
        }
        return size;
    }

    @Override
    public int onTrackedInsert(FluidStack stack, int size) {
        stack.amount = size;

        for (int i = this.tasks.size() - 1; i >= 0; i--) {
            Task task = this.tasks.get(i);
            if (!(task instanceof ProcessingTask))
                continue;

            size = task.supplyInput(stack);
            if (size < 0)
                return 0;
        }
        return size;
    }

    @Override
    public NBTTagCompound writeToNbt(NBTTagCompound tag) {
        //TODO: nbt writing of tasks
        return null;
    }

    @Override
    public void setCanUpdate(boolean canUpdate) {
        this.canUpdate = canUpdate;
    }

    @Override
    public boolean canUpdate() {
        return this.canUpdate;
    }

    @Override
    public List<ICraftingMonitorElement> getCraftingMonitorElements() {
        ICraftingMonitorElementList elements = API.instance().createCraftingMonitorElementList();
        List<Task> taskList = this.tasks;
        for (int i = 0; i < taskList.size(); i++) {
            Task task = taskList.get(i);
            if (i == 0 && task instanceof CraftingTask) {
                Output output = task.getOutputs().get(0);
                //TODO remove cast
                elements.add(new CraftingMonitorElementItemRender(output.getCompareableItemStack(), 0, 0, 0,
                        (int) task.getAmountNeeded()));
            }

            for (ICraftingMonitorElement craftingMonitorElement : task.getCraftingMonitorElements()) {
                elements.add(craftingMonitorElement);
            }
        }

        return elements.getElements();
    }

    @Override
    public int getCompletionPercentage() {
        //TODO: completion percentage
        return 0;
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public ICraftingRequestInfo getRequested() {
        return info;
    }

    @Override
    public ICraftingPattern getPattern() {
        return this.tasks.get(0).getPattern();
    }

    @Override
    public long getExecutionStarted() {
        return this.executionStarted;
    }

    @Override
    public IStackList<ItemStack> getMissing() {
        return this.missingItemStacks;
    }

    @Override
    public IStackList<FluidStack> getMissingFluids() {
        return this.missingFluidStacks;
    }

    @Override
    public int getQuantity() {
        return this.quantity;
    }

    @Override
    public long getCalculationTime() {
        return this.calculationTime;
    }
}
