package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.google.common.collect.Sets;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementList;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementItemRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementMissingPatternRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingRequestInfo;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.DurabilityInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.InfiniteInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementFluidStack;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementItemStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.*;

public class MasterCraftingTask implements ICraftingTask {

    private static final String NBT_QUANTITY = "Quantity";
    private static final String NBT_TASKS = "Tasks";
    private static final String NBT_CAN_UPDATE = "CanUpdate";
    private static final String NBT_TICKS = "Ticks";
    private static final String NBT_EXECUTION_STARTED = "ExecutionStarted";
    private static final String NBT_TOTAL_AMOUNT_NEEDED = "TotalAmountNeeded";
    private static final String NBT_CALCULATION_TIME = "CalculationTime";
    private static final String NBT_UUID = "Uuid";
    private static final String NBT_REQUEST_INFO = "RequestInfo";
    private static final String NBT_CANCELLED = "Cancelled";

    /**
     * All tasks that make up this auto crafting request
     */
    private final List<Task> tasks = new ObjectArrayList<>();
    private IStackList<ItemStack> missingItemStacks = API.instance().createItemStackList();
    private IStackList<FluidStack> missingFluidStacks = API.instance().createFluidStackList();

    private final INetwork network;

    private final ICraftingRequestInfo info;

    private UUID id = UUID.randomUUID();

    private final long quantity;
    /**
     * Timestamp of when the execution started
     */
    private long executionStarted = -1;
    /**
     * The time in milli seconds it took for the calculation to complete
     */
    private long calculationTime = -1;
    private long ticks;
    private boolean canUpdate;
    private boolean cancelled;

    //completion percentage
    private long totalAmountNeeded;
    private int completionPercentage;

    //halted data
    private ItemStack missingPatternStack;
    private boolean halted;

    public MasterCraftingTask(@Nonnull INetwork network, @Nonnull ICraftingRequestInfo requested, long quantity,
                              @Nonnull ICraftingPattern pattern) {
        this.network = network;
        this.info = requested;

        long outputPerCraft = 0;
        if (requested.getItem() != null) {
            for (ItemStack output : pattern.getOutputs()) {
                if (API.instance().getComparer().isEqualNoQuantity(output, requested.getItem())) {
                    outputPerCraft = output.getCount();
                    break;
                }
            }
        } else {
            for (FluidStack output : pattern.getFluidOutputs()) {
                if (API.instance().getComparer().isEqual(output, requested.getFluid(), IComparer.COMPARE_NBT)) {
                    outputPerCraft = output.amount;
                    break;
                }
            }
        }

        //adjust quantity to next full output size
        this.quantity = (quantity % outputPerCraft) == 0 ? quantity : (quantity / outputPerCraft + 1) * outputPerCraft;

        if (pattern.isProcessing())
            tasks.add(new ProcessingTask(pattern, quantity, requested.getFluid() != null));
        else
            tasks.add(new CraftingTask(pattern, quantity));
    }

    public MasterCraftingTask(@Nonnull INetwork network, @Nonnull NBTTagCompound compound)
            throws CraftingTaskReadException {
        this.network = network;

        this.executionStarted = compound.getLong(NBT_EXECUTION_STARTED);
        this.calculationTime = compound.getLong(NBT_CALCULATION_TIME);
        this.totalAmountNeeded = compound.getLong(NBT_TOTAL_AMOUNT_NEEDED);
        this.canUpdate = compound.getBoolean(NBT_CAN_UPDATE);
        this.cancelled = compound.getBoolean(NBT_CANCELLED);
        this.quantity = compound.getInteger(NBT_QUANTITY);
        this.ticks = compound.getLong(NBT_TICKS);
        this.info = new CraftingRequestInfo(compound.getCompoundTag(NBT_REQUEST_INFO));
        this.id = compound.getUniqueId(NBT_UUID);

        NBTTagList list = compound.getTagList(NBT_TASKS, Constants.NBT.TAG_COMPOUND);
        if (list.tagCount() < 1)
            throw new CraftingTaskReadException("List of sub tasks is empty");

        Map<UUID, Task> taskMap = new Object2ObjectLinkedOpenHashMap<>();
        Map<UUID, List<UUID>> parentMap = new HashMap<>();

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound taskCompound = list.getCompoundTagAt(i);
            String type = taskCompound.getString(Task.NBT_TASK_TYPE);

            Task newTask;
            switch (type) {
                case CraftingTask.TYPE:
                    newTask = new CraftingTask(network, taskCompound);
                    break;
                case ProcessingTask.TYPE:
                    newTask = new ProcessingTask(network, taskCompound);
                    break;
                default:
                    throw new CraftingTaskReadException("Unknown task type: " + type);
            }

            taskMap.put(newTask.getUuid(), newTask);

            if (taskCompound.hasKey(Task.NBT_PARENT_UUIDS)) {
                List<UUID> parentUuids = new ArrayList<>();

                NBTTagList parentsTagList = taskCompound.getTagList(Task.NBT_PARENT_UUIDS, Constants.NBT.TAG_COMPOUND);
                if (parentsTagList.tagCount() < 1)
                    throw new CraftingTaskReadException("Parents tag exists but is empty");

                for (int j = 0; j < parentsTagList.tagCount(); j++)
                    parentUuids.add(parentsTagList.getCompoundTagAt(j).getUniqueId(NBT_UUID));

                parentMap.put(newTask.getUuid(), parentUuids);
            }
        }

        for (Map.Entry<UUID, List<UUID>> uuidListEntry : parentMap.entrySet()) {
            List<UUID> parents = uuidListEntry.getValue();
            Task child = taskMap.get(uuidListEntry.getKey());

            for (UUID uuid : parents) {
                Task parent = taskMap.get(uuid);

                if (child.getUuid().equals(uuid))
                    throw new CraftingTaskReadException("Parent of task is itself");
                if (parent == null)
                    throw new CraftingTaskReadException("Task references parent that doesn't exist");

                child.addParent(parent);
            }
        }

        this.tasks.addAll(taskMap.values());
    }

    @Override
    public boolean update() {
        if (!canUpdate)
            return false;
        if (executionStarted == -1)
            executionStarted = System.currentTimeMillis();

        boolean allFinished = true;

        long totalAmount = 0;

        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task task = tasks.get(i);
            if (task.isFinished())
                continue;

            Set<ICraftingPatternContainer> containers = network.getCraftingManager().getAllContainer(task.getPattern());
            int[] actualUpdateCounts = null;

            if (task instanceof ProcessingTask) {
                ProcessingTask pTask = (ProcessingTask) task;
                actualUpdateCounts = splitBetweenCraftingPatternContainers(pTask, containers);
            }

            int j = -1;
            for (ICraftingPatternContainer container : containers) {
                j++;
                //check if container is allowed to update
                if (this.ticks % container.getUpdateInterval() != 0)
                    continue;

                //get current update count
                int remainingUpdates = container.getCraftingUpdatesLeft();

                //stop if no updates are left
                if (remainingUpdates < 1)
                    continue;

                if (actualUpdateCounts != null)
                    remainingUpdates = actualUpdateCounts[j];

                container.useCraftingUpdates(task.update(network, container, remainingUpdates));
                if (task.isFinished())
                    break;
            }

            totalAmount += task.getAmountNeeded();

            if (!task.isFinished())
                allFinished = false;
        }

        this.completionPercentage = (int) (100 - Math.ceil(totalAmount * 100d / (double) this.totalAmountNeeded));

        this.ticks++;
        return allFinished;
    }

    /**
     * Returns an array which contains the allowed updates for each valid crafting pattern container.
     * Not the most efficient method, but it works.
     *
     * @param task       the task
     * @param containers the set of containers
     * @return an array which contains the available crafting updates for each container in a filtered subset.
     */
    private int[] splitBetweenCraftingPatternContainers(ProcessingTask task, Set<ICraftingPatternContainer> containers) {
        if (containers.size() < 2)
            return null;

        int total = 0;
        for (ICraftingPatternContainer container : containers) {
            total += container.getCraftingUpdatesLeft();
        }

        //the amount of sets that can be inserted
        boolean seen = false;
        long best = 0;
        List<Input> inputs = task.getInputs();
        for (Input in : inputs) {
            long l = in.getTotalInputAmount() / in.getQuantityPerCraft();
            if (!seen || l < best) {
                seen = true;
                best = l;
            }
        }

        if (!seen)
            throw new IllegalStateException();

        total = (int) Math.min(total, best);

        if (total < 1)
            return null;

        Deque<Pair<ICraftingPatternContainer, Integer>> queue = new LinkedList<>();
        ICraftingPatternContainer[] array = containers.toArray(new ICraftingPatternContainer[0]);

        if (task.getCrafterIndex() >= array.length)
            task.setCrafterIndex(0);

        for (int i = task.getCrafterIndex(); i < array.length; i++) {
            ICraftingPatternContainer filteredContainer = array[i];

            if (filteredContainer.getCraftingUpdatesLeft() > 0 && this.ticks % filteredContainer.getUpdateInterval() == 0)
                queue.offerLast(Pair.of(filteredContainer, i));

            if (task.getCrafterIndex() != 0 && i == task.getCrafterIndex() - 1)
                break;

            if (task.getCrafterIndex() != 0 && i == array.length - 1)
                i = -1;
        }

        int[] actualUpdateCounts = new int[array.length];
        Arrays.fill(actualUpdateCounts, 0);

        while (!queue.isEmpty()) {
            Pair<ICraftingPatternContainer, Integer> c = queue.pollFirst();

            int index = c.getRight();
            actualUpdateCounts[index]++;
            total--;

            if (total < 1) {
                task.setCrafterIndex(index + 1);
                break;
            }

            if (c.getLeft().getCraftingUpdatesLeft() - actualUpdateCounts[index] > 0)
                queue.offerLast(c);
        }

        return actualUpdateCounts;
    }

    @Override
    public ICraftingTaskError calculate() {
        long calculationStarted = System.currentTimeMillis();

        Task rootTask = tasks.get(0);

        CalculationResult result = rootTask.calculate(network,
                new ObjectArrayList<>(),
                Sets.newHashSet(rootTask.getPattern()),
                calculationStarted
        );

        //instantly cancel if calculation had any error, saver than waiting for the player to cancel
        if (result.getError() == null) {
            this.tasks.addAll(result.getNewTasks());

            this.missingItemStacks = result.getMissingItemStacks();
            this.missingFluidStacks = result.getMissingFluidStacks();

            this.calculationTime = System.currentTimeMillis() - calculationStarted;

            this.totalAmountNeeded = this.tasks.stream().mapToLong(Task::getAmountNeeded).sum();
        }

        //instantly cancel if anything went wrong
        if (result.getError() != null || hasMissing())
            onCancelled();

        return result.getError();
    }

    @Override
    public void onCancelled() {
        if (cancelled)
            return;
        cancelled = true;

        //just insert all stored items back into network
        for (Task task : this.tasks) {
            //insert loose items and fluids
            for (ItemStack looseItemStack : task.getLooseItemStacks())
                network.insertItem(looseItemStack, (long) looseItemStack.getCount(), Action.PERFORM);

            for (FluidStack looseFluidStack : task.getLooseFluidStacks())
                network.insertFluid(looseFluidStack, (long) looseFluidStack.amount, Action.PERFORM);

            //insert items that are still inside of inputs
            for (Input input : task.getInputs()) {
                boolean isDurabilityInput = input instanceof DurabilityInput;

                if (input instanceof InfiniteInput && !((InfiniteInput) input).containsItem())
                    continue;

                List<ItemStack> itemStacks = input.getItemStacks();
                for (int i = 0; i < itemStacks.size(); i++) {
                    ItemStack itemStack = itemStacks.get(i);

                    long amount = 1;

                    if (!isDurabilityInput) {
                        amount = input.getCurrentInputCounts().get(i);
                    } else { //set correct durability to item stack
                        itemStack.setItemDamage(
                                itemStack.getMaxDamage() - input.getCurrentInputCounts().get(i).intValue() + 1);
                    }

                    if (amount > 0 && (!isDurabilityInput ||
                            itemStack.getItemDamage() <= itemStack.getMaxDamage()))
                        network.insertItem(itemStack, amount, Action.PERFORM);
                }

                if (input.isFluid()) {
                    long amount = input.getCurrentInputCounts().get(0);
                    if (amount > 0)
                        network.insertFluid(input.getFluidStack(), amount, Action.PERFORM);
                }
            }

            //unlock all crafters
            if (!task.isFinished()) {
                for (ICraftingPatternContainer craftingPatternContainer :
                        network.getCraftingManager().getAllContainer(task.getPattern()))
                    craftingPatternContainer.unlock();
            }
        }
    }

    @Override
    public void updateHaltedState() {
        for (Task task : this.tasks) {
            if (!task.isFinished() && !network.getCraftingManager().getPatterns().contains(task.getPattern())) {
                this.halted = true;
                this.missingPatternStack = task.getPattern().getStack();
                return;
            }
        }

        this.halted = false;
        this.missingPatternStack = null;
    }

    @Override
    public boolean isHalted() {
        return this.halted;
    }

    @Override
    public int onTrackedInsert(ItemStack stack, int trackedAmount) {
        for (int i = this.tasks.size() - 1; i >= 0; i--) {
            Task task = this.tasks.get(i);
            if (!(task instanceof ProcessingTask))
                continue;

            int oldStackSize = stack.getCount();
            trackedAmount = ((ProcessingTask) task).supplyOutput(stack, trackedAmount);

            //make sure tracked amount is not bigger than stack size
            if (oldStackSize != stack.getCount())
                trackedAmount = Math.max(trackedAmount - (oldStackSize - stack.getCount()), 0);

            if (stack.isEmpty())
                break;
        }

        return trackedAmount;
    }

    @Override
    public int onTrackedInsert(FluidStack stack, int trackedAmount) {
        for (int i = this.tasks.size() - 1; i >= 0; i--) {
            Task task = this.tasks.get(i);
            if (!(task instanceof ProcessingTask))
                continue;

            int oldStackSize = stack.amount;
            trackedAmount = ((ProcessingTask) task).supplyOutput(stack, trackedAmount);

            if (oldStackSize != stack.amount)
                trackedAmount = Math.max(trackedAmount - (oldStackSize - stack.amount), 0);

            if (stack.amount < 1)
                break;
        }

        return trackedAmount;
    }

    @Override
    public NBTTagCompound writeToNbt(NBTTagCompound compound) {
        compound.setUniqueId(NBT_UUID, this.id);
        compound.setLong(NBT_QUANTITY, this.quantity);
        compound.setLong(NBT_CALCULATION_TIME, this.calculationTime);
        compound.setLong(NBT_EXECUTION_STARTED, this.executionStarted);
        compound.setLong(NBT_TOTAL_AMOUNT_NEEDED, this.totalAmountNeeded);
        compound.setBoolean(NBT_CAN_UPDATE, this.canUpdate);
        compound.setBoolean(NBT_CANCELLED, this.cancelled);
        compound.setLong(NBT_TICKS, this.ticks);
        compound.setTag(NBT_REQUEST_INFO, this.info.writeToNbt());

        NBTTagList list = new NBTTagList();
        for (Task task : this.tasks) {
            list.appendTag(task.writeToNbt(new NBTTagCompound()));
        }

        compound.setTag(NBT_TASKS, list);
        return compound;
    }

    @Override
    public List<ICraftingPreviewElement<?>> getPreviewStacks() {
        List<ICraftingPreviewElement<?>> elements = new ArrayList<>(50);

        //Output
        if (this.info.getItem() != null)
            elements.add(new CraftingPreviewElementItemStack(this.info.getItem(), 0, false, this.quantity));
        else if (this.info.getFluid() != null)
            elements.add(new CraftingPreviewElementFluidStack(this.info.getFluid(), 0, false, this.quantity));

        //Missing
        for (StackListEntry<ItemStack> entry : this.missingItemStacks.getStacks()) {
            ItemStack itemStack = entry.getStack();

            CraftingPreviewElementItemStack element =
                    new CraftingPreviewElementItemStack(itemStack, 0, true, entry.getCount());
            elements.add(element);
        }

        for (StackListEntry<FluidStack> entry : this.missingFluidStacks.getStacks()) {
            FluidStack fluidStack = entry.getStack();

            CraftingPreviewElementFluidStack element =
                    new CraftingPreviewElementFluidStack(fluidStack, 0, true, entry.getCount());
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

        if (!this.isHalted()) {
            List<Task> taskList = this.tasks;
            for (int i = 0; i < taskList.size(); i++) {
                Task task = taskList.get(i);
                if (i == 0 && task instanceof CraftingTask) {
                    Output output = task.getOutputs().get(0);

                    elements.add(new CraftingMonitorElementItemRender(output.getCompareableItemStack(), 0, 0, 0,
                            task.getAmountNeeded() * output.getQuantityPerCraft()));
                }

                for (ICraftingMonitorElement craftingMonitorElement : task.getCraftingMonitorElements()) {
                    elements.add(craftingMonitorElement);
                }
            }
        } else {
            elements.add(new CraftingMonitorElementMissingPatternRender(this.missingPatternStack));
        }

        elements.commit();
        elements.clearEmptyElements();
        elements.sort();

        return elements.getElements();
    }

    @Override
    public int getCompletionPercentage() {
        return this.completionPercentage;
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
    public long getQuantity() {
        return this.quantity;
    }

    @Override
    public long getCalculationTime() {
        return this.calculationTime;
    }
}