package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementError;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementFluidRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementItemRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.RestockableInput;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a processing task
 */
public class ProcessingTask extends Task {

    public static final String TYPE = "processing";

    private static final String NBT_STATE = "State";
    private static final String NBT_HAS_ITEM_INPUTS = "HasItemInputs";
    private static final String NBT_HAS_FLUID_INPUTS = "HasFluidInputs";

    private boolean finished;

    private final List<Pair<Input, Integer>> generatedPairs = new ObjectArrayList<>(this.inputs.size());

    private ProcessingState state = ProcessingState.READY;

    private final boolean hasFluidInputs;
    private final boolean hasItemInputs;

    public ProcessingTask(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        super(pattern, amountNeeded, isFluidRequested);
        this.hasFluidInputs = this.inputs.stream().anyMatch(Input::isFluid);
        this.hasItemInputs = this.inputs.stream().anyMatch(i -> !i.isFluid());
    }

    public ProcessingTask(@Nonnull INetwork network, @Nonnull NBTTagCompound compound)
            throws CraftingTaskReadException {
        super(network, compound);
        if (this.amountNeeded < 1)
            this.finished = true;

        this.hasItemInputs = compound.getBoolean(NBT_HAS_ITEM_INPUTS);
        this.hasFluidInputs = compound.getBoolean(NBT_HAS_FLUID_INPUTS);

        try {
            this.state = ProcessingState.valueOf(compound.getString(NBT_STATE));
        } catch (IllegalArgumentException e) {
            throw new CraftingTaskReadException("Processing task has unknown state");
        }
    }

    @Override
    public int update(@Nonnull INetwork network, @Nonnull ICraftingPatternContainer container, int toCraft) {
        //stop if task is finished
        if (this.amountNeeded < 1) {
            this.finished = true;
            network.getCraftingManager().onTaskChanged();
            return 0;
        }

        //limit by amount needed
        if (toCraft > this.amountNeeded)
            toCraft = (int) this.amountNeeded;

        //limit by input counts
        for (Input input : this.inputs)
            toCraft = (int) Math.min(input.getMinimumCraftableAmount(), toCraft);

        if (toCraft < 1)
            return 0;

        //limit by crafter mode
        if (container.getCrafterMode() == ICraftingPatternContainer.CrafterMode.PULSE_INSERTS_NEXT_SET)
            toCraft = 1;

        //machine is locked
        if (container.isLocked()) {
            this.state = ProcessingState.LOCKED;
            network.getCraftingManager().onTaskChanged();
            return 0;
        }

        //no connected machine
        if ((hasFluidInputs && container.getConnectedFluidInventory() == null) ||
                (hasItemInputs && container.getConnectedInventory() == null)) {
            this.state = ProcessingState.MACHINE_NONE;
            network.getCraftingManager().onTaskChanged();
            return 0;
        }

        List<Pair<Input, Integer>> pairs = tryInsertIntoContainer(container, toCraft);

        //something couldn't be inserted at all
        if (pairs.isEmpty()) {
            this.state = ProcessingState.MACHINE_DOES_NOT_ACCEPT;
            network.getCraftingManager().onTaskChanged();
            return 0;
        }

        toCraft = pairs.stream()
                .mapToInt(p -> (int) Math.floor(p.getRight() / (double) p.getLeft().getQuantityPerCraft())).min()
                .orElseThrow(IllegalStateException::new);

        //if we could insert something but not at least a full set, return
        if (toCraft < 1) {
            this.state = ProcessingState.MACHINE_DOES_NOT_ACCEPT;
            network.getCraftingManager().onTaskChanged();
            return 0;
        } else {
            //actually try to insert now
            generateAndInsertIntoContainer(container, toCraft);
        }

        container.onUsedForProcessing();
        this.state = ProcessingState.READY;

        //this is done in supplyInput instead to avoid an early finish
        //this.amountNeeded -= toCraft;

        //if there's no remainder and the task has crafted everything, we're done
        if (this.amountNeeded < 1)
            this.finished = true;

        network.getCraftingManager().onTaskChanged();
        return toCraft;
    }

    /**
     * Called when a tracked item is imported. Forwards imported items to parents if this is a processing task.
     *
     * @param stack         the imported stack (this stack is modified)
     * @param trackedAmount the amount of the stack that already has been tracked
     * @return the amount of the stack amount that has been tracked
     */
    public int supplyOutput(ItemStack stack, int trackedAmount) {
        //if there's anything left and the item is an output of this processing task -> forward to parents
        Output matchingOutput = this.outputs.stream()
                .filter(o -> !o.isFluid() &&
                        API.instance().getComparer().isEqualNoQuantity(o.getCompareableItemStack(), stack))
                .findFirst().orElse(null);

        RestockableInput matchingInput = (RestockableInput) this.inputs.stream()
                .filter(input -> input instanceof RestockableInput && !input.isFluid() &&
                        API.instance().getComparer().isEqualNoQuantity(input.getCompareableItemStack(), stack))
                .findFirst().orElse(null);

        long inputRemainder = stack.getCount();
        //give item to restockable input
        if (matchingOutput != null && matchingInput != null && matchingInput.getAmountMissing() > 0 &&
                matchingOutput.getProcessingAmount() > 0)
            inputRemainder = matchingInput.increaseItemStackAmount(stack, stack.getCount());

        if (matchingOutput != null && matchingOutput.getProcessingAmount() > 0) {

            int newlyTrackedAmount = 0;

            //only track what hasn't been tracked
            if (stack.getCount() > trackedAmount) {
                //limit param using the already tracked amount
                newlyTrackedAmount = trackAndUpdate(matchingOutput, stack.getCount() - trackedAmount);
                trackedAmount += newlyTrackedAmount;
            }

            //subtract amount that was given to input and only allow giving to the parent what was actually tracked
            stack.setCount((int) Math.min(newlyTrackedAmount, inputRemainder));
            int newStackSize = stack.getCount();

            //distribute to parents
            if (!this.getParents().isEmpty()) {

                //loop through all parents while there is anything left to split up
                for (Iterator<Task> iterator = this.getParents().iterator(); !stack.isEmpty() && iterator.hasNext(); ) {
                    iterator.next().supplyInput(stack);
                }
            }

            stack.setCount((int) (inputRemainder - (newStackSize - stack.getCount())));
        }

        return trackedAmount;
    }

    /**
     * Called when a tracked fluid is imported. Forwards imported fluids to parents if this is a processing task.
     *
     * @param stack         the imported stack (this stack is modified)
     * @param trackedAmount the amount of the stack that already has been tracked
     * @return the amount of the stack amount that has been tracked
     */
    public int supplyOutput(FluidStack stack, int trackedAmount) {
        //if there's anything left and the item is an output of this processing task -> forward to parents
        Output matchingOutput = this.outputs.stream()
                .filter(o -> o.isFluid() &&
                        API.instance().getComparer().isEqual(o.getFluidStack(), stack, IComparer.COMPARE_NBT))
                .findFirst().orElse(null);

        RestockableInput matchingInput = (RestockableInput) this.inputs.stream()
                .filter(input -> input instanceof RestockableInput && input.isFluid() &&
                        API.instance().getComparer().isEqual(input.getFluidStack(), stack, IComparer.COMPARE_NBT))
                .findFirst().orElse(null);

        long inputRemainder = stack.amount;
        //give item to restockable input
        if (matchingOutput != null && matchingInput != null && matchingInput.getAmountMissing() > 0 &&
                matchingOutput.getProcessingAmount() > 0)
            inputRemainder = matchingInput.increaseFluidStackAmount(stack.amount);

        if (matchingOutput != null && matchingOutput.getProcessingAmount() > 0) {

            int newlyTrackedAmount = 0;

            //only track what hasn't been tracked
            if (stack.amount > trackedAmount) {
                //limit param using the already tracked amount
                newlyTrackedAmount = trackAndUpdate(matchingOutput, stack.amount - trackedAmount);
                trackedAmount += newlyTrackedAmount;
            }

            //subtract amount that was given to input and only allow giving to the parent what was actually tracked
            stack.amount = (int) Math.min(newlyTrackedAmount, inputRemainder);
            int newStackSize = stack.amount;

            //distribute to parents
            if (!this.getParents().isEmpty()) {

                //loop through all parents while there is anything left to split up
                for (Iterator<Task> iterator = this.getParents().iterator(); stack.amount > 0 && iterator.hasNext(); ) {
                    iterator.next().supplyInput(stack);
                }
            }

            stack.amount = (int) (inputRemainder - (newStackSize - stack.amount));
        }

        return trackedAmount;
    }

    /**
     * Updates the processing amount of the given output. How much of the given {@code trackableAmount} can be used is
     * very precisely calculated. Also checks if any new amount of sets are completed and notifies the inputs
     * accordingly.
     *
     * @param output          the output
     * @param trackableAmount the amount that was imported for the given output
     * @return how much of the given {@code trackableAmount} was used up
     */
    private int trackAndUpdate(Output output, long trackableAmount) {
        long outputProcessingAmount = output.getProcessingAmount();

        //amount of sets that have been inserted for all inputs
        int insertedInputSets = this.inputs.stream()
                .mapToInt(input -> (int) (input.getProcessingAmount() / input.getQuantityPerCraft())).min()
                .orElseThrow(IllegalStateException::new);

        //if there's at least one set inserted
        if (insertedInputSets > 0) {
            int remainder = (int) (outputProcessingAmount % output.getQuantityPerCraft());

            if (insertedInputSets == 1) { //only one set inserted
                //limit by the remainder or one full set
                trackableAmount = Math.min(trackableAmount,
                        remainder == 0 ? output.getQuantityPerCraft() : remainder);
            } else {
                //allow for [sets - 1] full batches and then add the same calculation as above
                trackableAmount = Math.min(trackableAmount,
                        (insertedInputSets - 1) * output.getQuantityPerCraft() +
                                (remainder == 0 ? output.getQuantityPerCraft() : remainder));
            }

            if (trackableAmount < 1)
                return 0;
        } else {
            return 0;
        }

        //every time we pass something to the parents, check if one full set is done
        //the following code is just meant for tracking and does not use up the amount in any way
        long oldCompletedSets = output.getCompletedSets();
        output.setProcessingAmount(output.getProcessingAmount() - trackableAmount);

        if (output.getProcessingAmount() < 1) { //if output is done complete all sets
            output.setCompletedSets(
                    (long) Math.ceil(output.getAmountNeeded() / (double) output.getQuantityPerCraft()));
        } else { //floor otherwise
            output.setCompletedSets(
                    (long) Math.floor((output.getAmountNeeded() - output.getProcessingAmount()) /
                            (double) output.getQuantityPerCraft()));
        }

        //calculate the amount of completed sets
        long smallestCompletedSetCount =
                this.outputs.stream().mapToLong(Output::getCompletedSets).min()
                        .orElseThrow(() -> new IllegalStateException("Outputs list is empty"));
        long newlyCompletedSets = smallestCompletedSetCount - oldCompletedSets;

        //if there are new sets that got completed by this insertion -> notify the inputs
        if (newlyCompletedSets > 0) {
            this.amountNeeded -= newlyCompletedSets;
            //subtract one full set from each input
            for (Input input : this.inputs)
                input.setProcessingAmount(
                        input.getProcessingAmount() - input.getQuantityPerCraft() * newlyCompletedSets);
        }

        return (int) (outputProcessingAmount - output.getProcessingAmount());
    }

    /**
     * Inserts the given {@code stack} into the given {@code destination}
     *
     * @param dest  the destination
     * @param stack the stack that should be inserted
     * @return the remainder that couldn't be inserted; an empty ItemStack otherwise
     */
    private ItemStack insertIntoInventory(@Nullable IItemHandler dest, @Nonnull ItemStack stack) {
        if (dest == null) {
            return stack;
        }

        for (int i = 0; i < dest.getSlots(); ++i) {
            stack = dest.insertItem(i, stack, false);

            if (stack.isEmpty())
                break;
        }

        return stack;
    }

    /**
     * Simulates the insertion of all the given {@code stacks} at once. Does not modify the given list itself, although
     * the counts of the ItemStacks within it will be modified. The count of each ItemStack will represent how much of
     * it could not be inserted.
     * <p>
     * This method tries to be very efficient by modifying the given ItemStacks instead of returning a new list.
     * It also does not perform a single call to {@link ItemStack#copy()}
     *
     * @param dest   the destination inventory
     * @param stacks the list of stacks which is operated on
     */
    private void simulateInsertionIntoInventory(@Nullable IItemHandler dest, @Nonnull List<ItemStack> stacks) {
        if (dest == null)
            return;

        //destination slot : Stack -> Inserted Amount
        Map<Integer, ItemStack> slotToStackMap = new HashMap<>();

        for (int i = 0; i < dest.getSlots(); i++) {
            for (int j = 0; j < stacks.size(); j++) {
                ItemStack stack = stacks.get(j);

                if (stack.isEmpty())
                    continue;

                ItemStack mapStack = slotToStackMap.get(i);
                ItemStack destStack = mapStack != null ? mapStack : dest.getStackInSlot(i);

                int prevCount = 0;
                //ensure not empty to fix comparison
                if (mapStack != null) {
                    prevCount = mapStack.getCount();
                    mapStack.setCount(1);
                }

                //check if item is allowed
                if ((!(destStack.isEmpty() && mapStack == null) && !API.instance().getComparer().isEqualNoQuantity(stack, destStack)) || !dest.isItemValid(i, stack)) {
                    if (mapStack != null)
                        mapStack.setCount(prevCount);
                    continue;
                }

                if (mapStack != null)
                    mapStack.setCount(prevCount);

                int insertedAmount = stack.getCount() - dest.insertItem(i, stack, true).getCount();

                if (mapStack == null) {
                    slotToStackMap.put(i, stack);
                }

                stack.shrink(insertedAmount);
            }
        }
    }

    /**
     * Generates new items and fluids and tries to insert them directly into the given {@code container}, but only
     * simulating the insertion. Returns information about how much could be inserted for each {@link Input}.
     *
     * @param container the container
     * @param toCraft   the amount of sets to insert
     * @return a list of pairs containing each an {@link Input} and the amount for that input that could be inserted;
     * if there was any {@link Input} of which nothing could be inserted, then an empty list is returned
     */
    private List<Pair<Input, Integer>> tryInsertIntoContainer(@Nonnull ICraftingPatternContainer container,
                                                              int toCraft) {
        this.generatedPairs.clear();

        IItemHandler connectedInventory = container.getConnectedInventory();
        IFluidHandler connectedFluidInventory = container.getConnectedFluidInventory();

        List<ItemStack> allItemStacks = new ArrayList<>(this.inputs.stream()
                .filter(i -> !i.isFluid()).mapToInt(i -> i.getCurrentInputCounts().size()).sum());

        //notify inputs and generate input items
        for (Input input : this.inputs) {
            int amount = toCraft * input.getQuantityPerCraft();

            if (input.isFluid()) {
                //saving this is better than copying the whole stack again
                int oldAmount = input.getFluidStack().amount;
                FluidStack newStack = input.getFluidStack();
                newStack.amount = amount;

                int insertedAmount = connectedFluidInventory.fill(newStack, false);

                if (insertedAmount < 1)
                    return Collections.emptyList();
                //save inserted amount
                this.generatedPairs.add(Pair.of(input, insertedAmount));

                newStack.amount = oldAmount;
            } else {
                long tempAmount = amount;

                LongArrayList inputCounts = input.getCurrentInputCounts();
                //generate new input counts. copied from Input#decreaseInputAmount
                //noinspection DuplicatedCode
                for (int i = 0; i < inputCounts.size(); i++) {
                    long currentInputCount = inputCounts.getLong(i);
                    if (currentInputCount == 0)
                        continue;
                    if (tempAmount < 1)
                        break;

                    long newInputCount = currentInputCount - tempAmount;
                    if (newInputCount < 0) {
                        tempAmount = -newInputCount;
                        newInputCount = 0L;
                    } else {
                        tempAmount = 0;
                    }

                    ItemStack stack = input.getItemStacks().get(i);
                    stack.setCount((int) (currentInputCount - newInputCount));
                    allItemStacks.add(stack);
                }
            }
        }

        simulateInsertionIntoInventory(connectedInventory, allItemStacks);

        for (int i = 0; i < allItemStacks.size(); i++) {
            ItemStack itemStack = allItemStacks.get(i);

            //obtain input back from flat mapped list
            int total = 0;
            boolean reverse = i > allItemStacks.size() / 2;
            for (ListIterator<Input> iterator = this.inputs.listIterator(reverse ? allItemStacks.size() : 0);
                 reverse ? iterator.hasPrevious() : iterator.hasNext(); ) {

                if (reverse) {
                    Input element = iterator.previous();
                    total += element.getCurrentInputCounts().size();
                }

                if (reverse ? this.inputs.size() - total >= i : total >= i) { //add found input to generated pairs
                    Input foundInput = this.inputs.get(reverse ? this.inputs.size() - total : total);

                    this.generatedPairs.add(Pair.of(foundInput, toCraft * foundInput.getQuantityPerCraft() - itemStack.getCount()));

                    //make sure none of these are empty, otherwise it will break comparisons
                    itemStack.setCount(1);
                    break;
                }

                if (!reverse) {
                    Input element = iterator.next();
                    total += element.getCurrentInputCounts().size();
                }
            }
        }

        return this.generatedPairs;
    }

    /**
     * Generates new items and fluids and tries to insert them directly into the given {@code container}. Also updates
     * the processing amount of inputs.
     *
     * @param container the container
     * @param toCraft   the amount of sets to insert
     */
    private void generateAndInsertIntoContainer(@Nonnull ICraftingPatternContainer container, int toCraft) {
        IItemHandler connectedInventory = container.getConnectedInventory();
        IFluidHandler connectedFluidInventory = container.getConnectedFluidInventory();

        //notify inputs and generate input items
        for (Input input : this.inputs) {
            //copy current input counts
            LongArrayList oldInputCounts = input.getCurrentInputCounts().clone();

            input.decreaseInputAmount((long) toCraft * input.getQuantityPerCraft());

            if (input.isFluid()) {
                FluidStack newStack = input.getFluidStack().copy();
                newStack.amount = input.getQuantityPerCraft() * toCraft;

                int remainder = newStack.amount - connectedFluidInventory.fill(newStack, true);

                //increase amount that is currently in the machine
                input.setProcessingAmount(input.getProcessingAmount() + (newStack.amount - remainder));
            } else {
                //this ensures that the correct item stacks are created when ore dict is being used
                for (int i = 0; i < oldInputCounts.size(); i++) {
                    long oldInputCount = oldInputCounts.getLong(i);
                    long newInputCount = input.getCurrentInputCounts().getLong(i);

                    long diff = oldInputCount - newInputCount;
                    if (diff < 1)
                        continue;

                    //generate new item using the difference in counts
                    ItemStack remainder = insertIntoInventory(connectedInventory,
                            ItemHandlerHelper.copyStackWithSize(input.getItemStacks().get(i), (int) diff));

                    //increase amount that is currently in the machine
                    input.setProcessingAmount(input.getProcessingAmount() + (diff - remainder.getCount()));
                }
            }
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        super.writeToNbt(compound);
        compound.setBoolean(NBT_HAS_ITEM_INPUTS, this.hasItemInputs);
        compound.setBoolean(NBT_HAS_FLUID_INPUTS, this.hasFluidInputs);
        compound.setString(NBT_STATE, this.state.toString());

        return compound;
    }

    @Nonnull
    @Override
    public String getTaskType() {
        return TYPE;
    }

    @Nonnull
    @Override
    public List<ICraftingMonitorElement> getCraftingMonitorElements() {
        if (isFinished())
            return Collections.emptyList();

        boolean hasError = this.state != ProcessingState.READY;
        List<ICraftingMonitorElement> elements = new ObjectArrayList<>(this.inputs.size() + this.outputs.size());

        //inputs
        for (Input input : this.inputs) {
            if (input.isFluid()) {
                CraftingMonitorElementFluidRender fluid =
                        new CraftingMonitorElementFluidRender(input.getFluidStack(),
                                input.getTotalInputAmount(), input.getProcessingAmount(), 0,
                                input.getToCraftAmount());
                elements.add(hasError ? getErrorElement(fluid) : fluid);
            } else {
                CraftingMonitorElementItemRender item =
                        new CraftingMonitorElementItemRender(input.getCompareableItemStack(),
                                input.getTotalInputAmount(), input.getProcessingAmount(), 0,
                                input.getToCraftAmount());
                elements.add(hasError ? getErrorElement(item) : item);
            }
        }

        //outputs
        for (Output output : this.outputs) {
            if (output.isFluid()) {
                elements.add(
                        new CraftingMonitorElementFluidRender(output.getFluidStack(), 0, 0,
                                output.getProcessingAmount(), 0));
            } else {
                elements.add(
                        new CraftingMonitorElementItemRender(output.getCompareableItemStack(), 0, 0,
                                output.getProcessingAmount(), 0));
            }
        }

        return elements;
    }

    @Nonnull
    @Override
    public List<ItemStack> getLooseItemStacks() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<FluidStack> getLooseFluidStacks() {
        return Collections.emptyList();
    }

    /**
     * Converts the given element in to an error element if this task has any error.
     *
     * @param base the base to wrap
     * @return if the state of this {@link ProcessingTask} is not {@link ProcessingState#READY}, then a
     * {@link CraftingMonitorElementError} wrapping the given {@code base} is returned; {@code null} otherwise
     */
    @Nullable
    private CraftingMonitorElementError getErrorElement(ICraftingMonitorElement base) {
        switch (this.state) {
            case LOCKED:
                return new CraftingMonitorElementError(base, "gui.refinedstorage:crafting_monitor.crafter_is_locked");
            case MACHINE_NONE:
                return new CraftingMonitorElementError(base, "gui.refinedstorage:crafting_monitor.machine_none");
            case MACHINE_DOES_NOT_ACCEPT:
                return new CraftingMonitorElementError(base,
                        "gui.refinedstorage:crafting_monitor.machine_does_not_accept");
            default:
                return null;
        }
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    /**
     * Defines the current processing state of a processing task
     */
    private enum ProcessingState {
        READY,
        MACHINE_NONE,
        MACHINE_DOES_NOT_ACCEPT,
        LOCKED
    }
}
