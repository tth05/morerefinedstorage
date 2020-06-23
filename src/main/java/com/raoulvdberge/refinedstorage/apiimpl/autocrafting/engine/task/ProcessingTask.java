package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a processing task
 */
public class ProcessingTask extends Task {

    public static final String TYPE = "processing";

    private static final String NBT_REMAINING_ITEMS = "RemainingItems";
    private static final String NBT_REMAINING_FLUIDS = "RemainingFluids";
    private static final String NBT_REMAINDER_CONTAINER_POS = "RemainderContainerPos";
    private static final String NBT_STATE = "State";
    private static final String NBT_HAS_ITEM_INPUTS = "HasItemInputs";
    private static final String NBT_HAS_FLUID_INPUTS = "HasFluidInputs";

    private boolean finished;
    private final List<ItemStack> remainingItems = new ObjectArrayList<>();
    private final List<FluidStack> remainingFluids = new ObjectArrayList<>();
    /**
     * Reference to the container that left behind the remainder. Ensures that all remainder is inserted into the
     * correct container.
     */
    private ICraftingPatternContainer remainderContainer;

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
        this.hasItemInputs = compound.getBoolean(NBT_HAS_ITEM_INPUTS);
        this.hasFluidInputs = compound.getBoolean(NBT_HAS_FLUID_INPUTS);

        try {
            this.state = ProcessingState.valueOf(compound.getString(NBT_STATE));
        } catch (IllegalArgumentException e) {
            throw new CraftingTaskReadException("Processing task has unknown state");
        }

        if (compound.hasKey(NBT_REMAINDER_CONTAINER_POS)) {
            BlockPos containerPos = BlockPos.fromLong(compound.getLong(NBT_REMAINDER_CONTAINER_POS));

            INetworkNode node = API.instance().getNetworkNodeManager(network.world()).getNode(containerPos);

            if (node == null) {
                throw new CraftingTaskReadException("No network node found at remainder container pos");
            } else if (node instanceof ICraftingPatternContainer) {
                this.remainderContainer = (ICraftingPatternContainer) node;
            } else {
                throw new CraftingTaskReadException(
                        "Remainer container of processing task is not a Crafting Pattern Container");
            }
        }

        if (compound.hasKey(NBT_REMAINING_ITEMS)) {
            NBTTagList items = compound.getTagList(NBT_REMAINING_ITEMS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                this.remainingItems.add(new ItemStack(items.getCompoundTagAt(i)));
            }
        }

        if (compound.hasKey(NBT_REMAINING_FLUIDS)) {
            NBTTagList fluids = compound.getTagList(NBT_REMAINING_FLUIDS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < fluids.tagCount(); i++) {
                this.remainingFluids.add(FluidStack.loadFluidStackFromNBT(fluids.getCompoundTagAt(i)));
            }
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

        //don't update if there's any remainder left from the previous update
        if (!this.remainingItems.isEmpty() || !this.remainingFluids.isEmpty()) {
            //only update if we get the same container again
            if (!container.equals(remainderContainer))
                return 0;

            //try to insert remainder until it's empty
            insertIntoContainer(container);

            if (remainingItems.isEmpty() && remainingFluids.isEmpty()) {
                container.onUsedForProcessing();
                this.state = ProcessingState.READY;
            }
            //always use up all crafting updates if there's any remainder left. blocks other tasks somewhat
            return toCraft;
        }

        remainderContainer = container;

        //adjust the craftable amount
        if (toCraft > this.amountNeeded)
            toCraft = (int) this.amountNeeded;

        for (Input input : this.inputs) {
            toCraft = (int) Math.min(input.getMinimumCraftableAmount(), toCraft);
        }

        if (toCraft < 1)
            return 0;

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

        generateAndInsertIntoContainer(container, toCraft);

        if (this.remainingItems.isEmpty() && this.remainingFluids.isEmpty()) { //everything went well
            container.onUsedForProcessing();
            this.state = ProcessingState.READY;
        } else { //couldn't insert everything
            this.state = ProcessingState.MACHINE_DOES_NOT_ACCEPT;
        }

        //this is done in supplyInput instead to avoid an early finish
        //this.amountNeeded -= toCraft;

        //if there's no remainder and the task has crafted everything, we're done
        if (this.amountNeeded < 1 && this.remainingItems.isEmpty() && this.remainingFluids.isEmpty())
            this.finished = true;

        network.getCraftingManager().onTaskChanged();
        return toCraft;
    }

    /**
     * Called when a tracked item is imported. Forwards imported items to parents if this is a processing task.
     *
     * @param stack the imported stack (this stack is modified)
     * @return the amount that was tracked but not actually used up
     */
    public long supplyOutput(ItemStack stack) {
        int trackedAmount = 0;

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

            long processingAmount = matchingOutput.getProcessingAmount();
            trackAndUpdate(matchingOutput, stack.getCount());
            trackedAmount = (int) (processingAmount - matchingOutput.getProcessingAmount());

            //subtract amount that was given to input
            stack.setCount((int) inputRemainder);

            //distribute to parents
            if (!this.getParents().isEmpty()) {

                //loop through all parents while there is anything left to split up
                for (Iterator<Task> iterator = this.getParents().iterator(); !stack.isEmpty() && iterator.hasNext(); ) {
                    iterator.next().supplyInput(stack);
                }
            }
        }

        return trackedAmount;
    }

    /**
     * Called when a tracked fluid is imported. Forwards imported fluids to parents if this is a processing task.
     *
     * @param stack the imported stack (this stack is modified)
     * @return the amount that was tracked but not actually used up
     */
    public int supplyOutput(FluidStack stack) {
        int trackedAmount = 0;

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

            long processingAmount = matchingOutput.getProcessingAmount();
            trackAndUpdate(matchingOutput, stack.amount);
            trackedAmount = (int) (processingAmount - matchingOutput.getProcessingAmount());

            //subtract amount that was given to input
            stack.amount = (int) inputRemainder;

            //distribute to parents
            if (!this.getParents().isEmpty()) {

                //loop through all parents while there is anything left to split up
                for (Iterator<Task> iterator = this.getParents().iterator(); stack.amount > 0 && iterator.hasNext(); ) {
                    iterator.next().supplyInput(stack);
                }
            }
        }

        return trackedAmount;
    }

    /**
     * Updates the processing amount of all outputs. Also check if any new amount of sets are completed and notifies the
     * inputs accordingly.
     *
     * @param output the output
     * @param amount the amount that was imported for the given output
     */
    private void trackAndUpdate(Output output, long amount) {
        //every time we pass something to the parents, check if one full set is done
        //the following code is just meant for tracking and does not use up the amount in any way
        long oldCompletedSets = output.getCompletedSets();
        output.setProcessingAmount(output.getProcessingAmount() - amount);

        //calculate new completed set amount
        output.setCompletedSets(
                (long) Math.floor((output.getAmountNeeded() - output.getProcessingAmount()) /
                        (double) output.getQuantityPerCraft()));

        //calculate the amount of completed sets
        long smallestCompletedSetCount =
                this.outputs.stream().mapToLong(Output::getCompletedSets).min()
                        .orElseThrow(() -> new RuntimeException("Outputs list is empty"));
        long newlyCompletedSets = smallestCompletedSetCount - oldCompletedSets;

        //if there are new sets that got completed by this insertion -> notify the inputs
        if (newlyCompletedSets > 0) {
            this.amountNeeded -= newlyCompletedSets;
            //subtract one full set from each input
            for (Input input : this.inputs)
                input.setProcessingAmount(
                        input.getProcessingAmount() - input.getQuantityPerCraft() * newlyCompletedSets);
        }
    }

    /**
     * Inserts the give {@code stack} into the given {@code destination}
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
            // .copy() is mandatory!
            stack = dest.insertItem(i, stack.copy(), false);

            if (stack.isEmpty())
                break;
        }

        return stack;
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
            List<Long> inputCounts = ((LongArrayList) input.getCurrentInputCounts()).clone();

            input.decreaseInputAmount(toCraft * input.getQuantityPerCraft());

            if (input.isFluid()) {
                FluidStack newStack = input.getFluidStack().copy();
                newStack.amount = input.getQuantityPerCraft() * toCraft;

                int remainder = newStack.amount - connectedFluidInventory.fill(newStack, true);

                //increase amount that is currently in the machine
                input.setProcessingAmount(input.getProcessingAmount() + (newStack.amount - remainder));

                //save everything that couldn't be inserted
                if (remainder > 0) {
                    newStack.amount = remainder;
                    remainingFluids.add(newStack);
                }
            } else {
                //this ensures that the correct item stacks are created when ore dict is being used
                for (int i = 0; i < inputCounts.size(); i++) {
                    Long oldInputCount = inputCounts.get(i);
                    Long newInputCount = input.getCurrentInputCounts().get(i);

                    long diff = oldInputCount - newInputCount;
                    if (diff < 1)
                        continue;

                    //generate new item using the difference in counts
                    ItemStack remainder = insertIntoInventory(connectedInventory,
                            ItemHandlerHelper.copyStackWithSize(input.getItemStacks().get(i), (int) diff));

                    //increase amount that is currently in the machine
                    input.setProcessingAmount(input.getProcessingAmount() + (diff - remainder.getCount()));

                    //save everything that couldn't be inserted
                    if (!remainder.isEmpty())
                        remainingItems.add(remainder);
                }
            }
        }
    }

    /**
     * Tries to insert all {@code remainingItems} and {@code remainingFluids} into the given {@code container}. Also
     * updates the processing amount of inputs.
     *
     * @param container the container
     */
    private void insertIntoContainer(@Nonnull ICraftingPatternContainer container) {
        IItemHandler connectedInventory = container.getConnectedInventory();
        IFluidHandler connectedFluidInventory = container.getConnectedFluidInventory();
        //insert generated items
        for (Iterator<ItemStack> iterator = remainingItems.iterator(); iterator.hasNext(); ) {
            ItemStack remainingItem = iterator.next();

            ItemStack remainder = insertIntoInventory(connectedInventory, remainingItem);
            //ugly -> store remaining stuff as (Input, int) pair
            Input input = this.inputs.stream().filter(i -> !i.isFluid() &&
                    i.getItemStacks().stream().anyMatch(
                            inputStack -> API.instance().getComparer().isEqualNoQuantity(inputStack, remainingItem)))
                    .findFirst().orElseThrow(RuntimeException::new);
            //increase amount that is currently in the machine
            input.setProcessingAmount(input.getProcessingAmount() + (remainingItem.getCount() - remainder.getCount()));

            if (remainder.isEmpty())
                iterator.remove();
            else
                remainingItem.setCount(remainder.getCount());
        }

        //insert generated fluids
        for (Iterator<FluidStack> iterator = remainingFluids.iterator(); iterator.hasNext(); ) {
            FluidStack remainingFluid = iterator.next();

            //noinspection ConstantConditions
            int remainder = remainingFluid.amount - connectedFluidInventory.fill(remainingFluid, true);
            //ugly
            Input input = this.inputs.stream().filter(i -> i.isFluid() &&
                    API.instance().getComparer().isEqual(i.getFluidStack(), remainingFluid, IComparer.COMPARE_NBT))
                    .findFirst().orElseThrow(RuntimeException::new);
            //increase amount that is currently in the machine
            input.setProcessingAmount(input.getProcessingAmount() + (remainingFluid.amount - remainder));

            if (remainder <= 0)
                iterator.remove();
            else
                remainingFluid.amount = remainder;
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        super.writeToNbt(compound);
        compound.setBoolean(NBT_HAS_ITEM_INPUTS, this.hasItemInputs);
        compound.setBoolean(NBT_HAS_FLUID_INPUTS, this.hasFluidInputs);

        if (this.remainderContainer != null)
            compound.setLong(NBT_REMAINDER_CONTAINER_POS, this.remainderContainer.getPosition().toLong());
        compound.setString(NBT_STATE, this.state.toString());

        if (!this.remainingItems.isEmpty()) {
            NBTTagList items = new NBTTagList();
            for (ItemStack remainingItem : this.remainingItems) {
                items.appendTag(remainingItem.writeToNBT(new NBTTagCompound()));
            }

            compound.setTag(NBT_REMAINING_ITEMS, items);
        }

        if (!this.remainingFluids.isEmpty()) {
            NBTTagList fluids = new NBTTagList();
            for (FluidStack remainingFluid : this.remainingFluids) {
                fluids.appendTag(remainingFluid.writeToNBT(new NBTTagCompound()));
            }

            compound.setTag(NBT_REMAINING_FLUIDS, fluids);
        }

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

        //count loose items as stored
        for (ItemStack remainingItem : this.remainingItems) {
            elements.add(new CraftingMonitorElementItemRender(remainingItem, remainingItem.getCount(), 0, 0, 0));
        }

        for (FluidStack remainingFluid : this.remainingFluids) {
            elements.add(new CraftingMonitorElementFluidRender(remainingFluid, remainingFluid.amount, 0, 0, 0));
        }

        //inputs
        for (Input input : this.inputs) {
            if (input.isFluid()) {
                //TODO: remove casts
                CraftingMonitorElementFluidRender fluid =
                        new CraftingMonitorElementFluidRender(input.getFluidStack(),
                                (int) input.getTotalInputAmount(), (int) input.getProcessingAmount(), 0,
                                (int) input.getToCraftAmount());
                elements.add(hasError ? getErrorElement(fluid) : fluid);
            } else {
                CraftingMonitorElementItemRender item =
                        new CraftingMonitorElementItemRender(input.getCompareableItemStack(),
                                (int) input.getTotalInputAmount(), (int) input.getProcessingAmount(), 0,
                                (int) input.getToCraftAmount());
                elements.add(hasError ? getErrorElement(item) : item);
            }
        }

        //ouputs
        for (Output output : this.outputs) {
            if (output.isFluid()) {
                elements.add(
                        new CraftingMonitorElementFluidRender(output.getFluidStack(), 0, 0,
                                (int) output.getProcessingAmount(), 0));
            } else {
                elements.add(
                        new CraftingMonitorElementItemRender(output.getCompareableItemStack(), 0, 0,
                                (int) output.getProcessingAmount(), 0));
            }
        }

        return elements;
    }

    @Nonnull
    @Override
    public List<ItemStack> getLooseItemStacks() {
        return this.remainingItems;
    }

    @Nonnull
    @Override
    public List<FluidStack> getLooseFluidStacks() {
        return this.remainingFluids;
    }

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
        }

        return null;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    private enum ProcessingState {
        READY,
        MACHINE_NONE,
        MACHINE_DOES_NOT_ACCEPT,
        LOCKED
    }
}
