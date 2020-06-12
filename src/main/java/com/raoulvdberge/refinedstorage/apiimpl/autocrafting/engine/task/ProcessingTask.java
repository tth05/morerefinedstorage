package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementError;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementFluidRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementItemRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
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

    @Override
    public int update(@Nonnull INetwork network, @Nonnull ICraftingPatternContainer container, int toCraft) {
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

        //stop if task is finished
        if (this.amountNeeded < 1) {
            this.finished = true;
            network.getCraftingManager().onTaskChanged();
            return 0;
        }

        //adjust the craftable amount
        if (toCraft > this.amountNeeded)
            toCraft = (int) this.amountNeeded;

        for (Input input : this.inputs) {
            toCraft = (int) Math.min(input.getMinimumCraftableAmount(), toCraft);
        }

        if (toCraft < 1)
            return 0;

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

        //notify inputs and generate input items
        for (Input input : this.inputs) {
            //copy current input counts
            List<Long> inputCounts = ((LongArrayList) input.getCurrentInputCounts()).clone();

            input.decreaseInputAmount(toCraft * input.getQuantityPerCraft());
            //increase amount that is currently in the machine
            input.scheduleSets(toCraft);

            if (input.isFluid()) {
                FluidStack newStack = input.getFluidStack().copy();
                newStack.amount = input.getQuantityPerCraft() * toCraft;
                remainingFluids.add(newStack);
            } else {
                //this ensures that the correct item stacks are created when ore dict is being used
                for (int i = 0; i < inputCounts.size(); i++) {
                    Long oldInputCount = inputCounts.get(i);
                    Long newInputCount = input.getCurrentInputCounts().get(i);

                    long diff = oldInputCount - newInputCount;
                    if (diff < 1)
                        continue;

                    //generate new item using the difference in counts
                    remainingItems.add(ItemHandlerHelper.copyStackWithSize(input.getItemStacks().get(i), (int) diff));
                }
            }
        }

        //notify outputs of new sets
        for (Output output : this.outputs)
            output.scheduleSets(toCraft);

        insertIntoContainer(container);

        if (this.remainingItems.isEmpty() && this.remainingFluids.isEmpty()) { //everything went well
            container.onUsedForProcessing();
            this.state = ProcessingState.READY;
        } else { //couldn't insert everything
            //TODO: processing amount on inputs and outputs is tied to remainder lists but not actual amount in machines
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
     * Tries to insert all {@code remainingItems} and {@code remainingFluids} into the given {@code container}
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
            if (remainder.isEmpty())
                iterator.remove();
            else
                remainingItem.setCount(remainder.getCount());
        }

        //insert generated fluids
        for (Iterator<FluidStack> iterator = remainingFluids.iterator(); iterator.hasNext(); ) {
            FluidStack remainingFluid = iterator.next();

            //noinspection ConstantConditions
            int remainder = connectedFluidInventory.fill(remainingFluid, true);
            if (remainder <= 0)
                iterator.remove();
            else
                remainingFluid.amount = remainder;
        }
    }

    @Nonnull
    @Override
    public List<ICraftingMonitorElement> getCraftingMonitorElements() {
        if (isFinished())
            return Collections.emptyList();

        //TODO: output should not show what is in the machine but rather what it globally expects

        boolean hasError = this.state != ProcessingState.READY;
        List<ICraftingMonitorElement> elements = new ObjectArrayList<>(this.inputs.size() + this.outputs.size());
        for (Input input : this.inputs) {
            if (input.isFluid()) {
                //TODO: remove casts
                CraftingMonitorElementFluidRender fluid =
                        new CraftingMonitorElementFluidRender(input.getFluidStack(), (int) input.getTotalInputAmount(),
                                0, 0, (int) input.getToCraftAmount());
                elements.add(hasError ? getErrorElement(fluid) : fluid);
            } else {
                CraftingMonitorElementItemRender item =
                        new CraftingMonitorElementItemRender(input.getCompareableItemStack(),
                                (int) input.getTotalInputAmount(), (int) input.getProcessingAmount(), 0,
                                (int) input.getToCraftAmount());
                elements.add(hasError ? getErrorElement(item) : item);
            }
        }

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

    /**
     * @return the current processing state
     */
    public ProcessingState getState() {
        return state;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    public enum ProcessingState {
        READY,
        MACHINE_NONE,
        MACHINE_DOES_NOT_ACCEPT,
        LOCKED
    }
}