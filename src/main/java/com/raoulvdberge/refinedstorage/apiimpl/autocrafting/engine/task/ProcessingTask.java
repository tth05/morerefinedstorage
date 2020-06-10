package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

            if(remainingItems.isEmpty() && remainingFluids.isEmpty()) {
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
            return 0;
        }

        //no connected machine
        if ((hasFluidInputs && container.getConnectedFluidInventory() == null) ||
                (hasItemInputs && container.getConnectedInventory() == null)) {
            this.state = ProcessingState.MACHINE_NONE;
            return 0;
        }

        //notify inputs and generate input items
        for (Input input : this.inputs) {
            //copy current input counts
            List<Long> inputCounts = ((LongArrayList) input.getCurrentInputCounts()).clone();

            input.decreaseInputAmount(toCraft * input.getQuantityPerCraft());

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

        insertIntoContainer(container);

        if (this.remainingItems.isEmpty() && this.remainingFluids.isEmpty()) { //everything went well
            container.onUsedForProcessing();
            this.state = ProcessingState.READY;
        } else { //couldn't insert everything
            this.state = ProcessingState.MACHINE_DOES_NOT_ACCEPT;
        }

        this.amountNeeded -= toCraft;

        //if there's no remainder and the task has crafted everything, we're done
        if (this.amountNeeded < 1 && this.remainingItems.isEmpty() && this.remainingFluids.isEmpty())
            this.finished = true;

        return toCraft;
    }

    /**
     * Inserts the give {@code stack} into the given {@code destination}
     * @param dest the destination
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