package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskErrorType;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingTaskError;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public abstract class Task {

    protected final List<Task> parents = new ObjectArrayList<>();
    protected final List<Input> inputs = new ObjectArrayList<>();
    protected final List<Output> outputs = new ObjectArrayList<>();

    protected final ICraftingPattern pattern;
    protected long amountNeeded;

    public Task(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        //merge all pattern item inputs
        for (NonNullList<ItemStack> itemStacks : pattern.getInputs()) {
            if (itemStacks.isEmpty())
                continue;

            Input newInput = null;

            //only if there any by-products, check for infinites and re-useables
            if (!pattern.isProcessing() && pattern.getByproducts().stream().anyMatch(i -> !i.isEmpty())) {

                //detect infinites
                for (ItemStack itemStack : itemStacks) {
                    //Create new matrix with oredicted component. This is needed for stuff like the infusion crystal
                    // from MA with oredict enabled. This is because the oredicted list then contains the infusion
                    // crystal which has durabiltiy and the master infusion crystal which is infinite. This code ensures
                    // that the master crystal is detected and also preferred.
                    NonNullList<ItemStack> matrix = NonNullList.create();
                    for (NonNullList<ItemStack> input : pattern.getInputs()) {
                        if (input.isEmpty()) {
                            matrix.add(ItemStack.EMPTY);
                            continue;
                        }

                        ItemStack patternInputItem = input.get(0);
                        if (API.instance().getComparer().isEqual(patternInputItem, itemStacks.get(0)))
                            matrix.add(itemStack);
                        else
                            matrix.add(patternInputItem);
                    }

                    //check if input is exactly the same in the remainder -> then it's infinite
                    for (ItemStack remainder : pattern.getByproducts(matrix)) {
                        //find item in by products and check if one damage was used up. this means that damage = uses
                        if (API.instance().getComparer().isEqual(itemStack, remainder)) {
                            //item was found in remainder staying exactly the same -> infinite input
                            newInput = new InfiniteInput(itemStack, pattern.isOredict());
                            break;
                        }
                    }
                }

                //detect re-useable items
                ItemStack itemStack = itemStacks.get(0);
                //damageable items won't be oredicted (hopefully)
                if (itemStacks.size() < 2 && newInput == null && itemStack.isItemStackDamageable()) {
                    for (ItemStack remainder : pattern.getByproducts()) {
                        //find item in by products and check if one damage was used up. this means that damage = uses
                        if (API.instance().getComparer()
                                .isEqual(itemStack, remainder,
                                        IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY) &&
                                remainder.getItemDamage() - 1 == itemStack.getItemDamage()) {
                            //item was found with one more damage in remainder, then it's a durability input
                            newInput = new DurabilityInput(itemStack, amountNeeded, pattern.isOredict());
                            break;
                        }
                    }
                }
            }

            //check for inputs that appear in the output for processing patterns
            if (pattern.isProcessing()) {
                //loop through all possibilities
                for (ItemStack inputItemStack : itemStacks) {
                    //loop through all outputs
                    for (ItemStack output : pattern.getOutputs()) {
                        //find the possibility that occurs in the output
                        if (API.instance().getComparer().isEqualNoQuantity(inputItemStack, output)) {
                            newInput = new RestockableInput(inputItemStack, inputItemStack.getCount(),
                                    pattern.isOredict());
                        }
                    }
                }
            }

            //if it's not a durability or infinite input, then just use a normal input
            if (newInput == null)
                newInput = new Input(itemStacks, amountNeeded, pattern.isOredict());

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern fluid inputs
        for (FluidStack i : pattern.getFluidInputs()) {
            Input newInput = new Input(i, amountNeeded, pattern.isOredict());

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern item outputs
        for (ItemStack itemStack : pattern.getOutputs()) {
            Output newOutput = new Output(itemStack, itemStack.getCount());

            //lovely cast
            mergeIntoList(newOutput, (List<Input>) (List<?>) this.outputs);
        }

        //merge all pattern fluid outputs
        for (FluidStack fluidStack : pattern.getFluidOutputs()) {
            Output newOutput = new Output(fluidStack, fluidStack.amount);

            mergeIntoList(newOutput, (List<Input>) (List<?>) this.outputs);
        }

        //decrease output amounts if restockable input exists
        for (Input input : this.inputs) {
            if (!(input instanceof RestockableInput))
                continue;

            for (Output output : this.outputs) {
                if (API.instance().getComparer()
                        .isEqualNoQuantity(output.getCompareableItemStack(), input.getCompareableItemStack())) {
                    long remainder = output.applyRestockableInput((RestockableInput) input);
                    //input cannot be satisfied with the output, therefore the missing items have to be normally
                    // extracted or crafted.
                    if (remainder != 0) {
                        //decrease restockable input QPC to correct amount because some of that is now handled by a
                        // different input
                        ((RestockableInput) input).fixCounts((int) (input.getQuantityPerCraft() - remainder));
                        //add new input with remainder as quantity per craft. Amount needed is set later to the correct
                        // amount
                        mergeIntoList(new Input(NonNullList.from(ItemStack.EMPTY,
                                ItemHandlerHelper.copyStackWithSize(input.getCompareableItemStack(), (int) remainder)),
                                1,
                                pattern.isOredict()), this.inputs);
                    }

                    break;
                }
            }
        }

        //find smallest output counts
        int smallestOutputStackSize = Integer.MAX_VALUE;
        int smallestOutputFluidStackSize = Integer.MAX_VALUE;

        for (Output output : this.outputs) {
            if (output.getQuantityPerCraft() < 1)
                continue;

            if (!output.isFluid()) {
                smallestOutputStackSize = Math.min(smallestOutputStackSize, output.getQuantityPerCraft());
            } else {
                smallestOutputFluidStackSize = Math.min(smallestOutputFluidStackSize, output.getQuantityPerCraft());
            }
        }

        //calculate actual needed amount, basically the amount of iterations that have to be run
        amountNeeded = (long) Math.ceil((double) amountNeeded / (double) (isFluidRequested ?
                smallestOutputFluidStackSize : smallestOutputStackSize));

        //set correct amount for all inputs
        for (Input input : this.inputs) {
            input.setAmountNeeded(amountNeeded * input.getQuantityPerCraft());
        }

        this.pattern = pattern;
        this.amountNeeded = amountNeeded;
    }

    /**
     * @param toCraft   the amount that this task is allowed to craft
     * @param container the container that should be used
     * @return the amount of crafting updates that were used up by this task
     */
    public abstract int update(@Nonnull INetwork network, @Nonnull ICraftingPatternContainer container, int toCraft);

    /**
     * @return all crafting monitor elements that are managed by this task
     */
    @Nonnull
    public abstract List<ICraftingMonitorElement> getCraftingMonitorElements();

    /**
     * This also replaces {@code ProcessingState.PROCESSED}
     *
     * @return whether or not this task is finished
     */
    public abstract boolean isFinished();

    /**
     * Supplies an input to this task. Called by sub tasks when they crafted something or when a tracked item is
     * imported. Also forwards imported items to parents if this is a processing task.
     *
     * @param stack the stack to supply
     * @return see {@link Input#decreaseToCraftAmount(ItemStack, long)}
     */
    protected int supplyInput(ItemStack stack) {
        long remainder = stack.getCount();
        if (!isFinished()) {
            //give to all inputs while there's anything left
            for (Input input : this.inputs) {
                long returnValue = input.decreaseToCraftAmount(stack, remainder);

                //no remainder left -> just return
                if (returnValue == -2)
                    return 0;
                else if (returnValue != -1) //go into next iteration otherwise
                    remainder = returnValue;
            }
        }

        //if there's anything left and the item is an output of this processing task -> forward to parents
        Output matchingOutput = this.outputs.stream()
                .filter(o -> API.instance().getComparer().isEqualNoQuantity(o.getCompareableItemStack(), stack))
                .findFirst().orElse(null);
        if (this instanceof ProcessingTask && remainder > 0 && matchingOutput != null) {
            //every time we pass something to the parents, check if one full set is done
            long previousSets = matchingOutput.getCurrentlyProcessingSetsCount();
            matchingOutput.setProcessingAmount(matchingOutput.getProcessingAmount() - remainder);

            //if amount of inserted sets decreased and all outputs are at that state now,
            // then one full set is DONE
            //TODO: does not work if more than one set is done at once -> find highest check if that's lower then that's the amount
            if (previousSets > matchingOutput.getCurrentlyProcessingSetsCount() &&
                    this.outputs.stream().allMatch(o -> o.getCurrentlyProcessingSetsCount() == previousSets - 1)) {
                this.amountNeeded--;
                //subtract one full set from each input
                for (Input input : this.inputs)
                    input.setProcessingAmount(input.getProcessingAmount() - input.getQuantityPerCraft());
            }

            //distribute to parents
            if(!this.getParents().isEmpty()) {
                stack.setCount((int) remainder);
                //loop through all parents while there is anything left to split up
                for (Iterator<Task> iterator = this.getParents().iterator(); !stack.isEmpty() && iterator.hasNext(); ) {
                    Task parent = iterator.next();
                    remainder = parent.supplyInput(stack);
                    //remove if there's nothing left
                    if (remainder == -1) {
                        return 0;
                    } else {
                        stack.setCount((int) remainder);
                    }
                }
            }
        }

        return remainder < 0 ? 0 : (int) remainder;
    }

    /**
     * Supplies an input to this task. Called when a tracked fluid is imported. Also forwards imported fluids to parents
     * if this is a processing task.
     *
     * @param stack the stack to supply
     * @return see {@link Input#decreaseToCraftAmount(FluidStack, long)}
     */
    protected int supplyInput(FluidStack stack) {
        long remainder = stack.amount;
        if (!isFinished()) {
            //give to all inputs while there's anything left
            for (Input input : this.inputs) {
                long returnValue = input.decreaseToCraftAmount(stack, remainder);

                //no remainder left -> just return
                if (returnValue == -2)
                    return 0;
                else if (returnValue != -1) //go into next iteration otherwise
                    remainder = returnValue;
            }
        }

        //if there's anything left and the item is an output of this processing task -> forward to parents
        Output matchingOutput = this.outputs.stream()
                .filter(o -> API.instance().getComparer().isEqual(o.getFluidStack(), stack, IComparer.COMPARE_NBT))
                .findFirst().orElse(null);
        if (this instanceof ProcessingTask && remainder > 0 && matchingOutput != null) {
            //every time we pass something to the parents, check if one full set is done
            long previousSets = matchingOutput.getCurrentlyProcessingSetsCount();
            matchingOutput.setProcessingAmount(matchingOutput.getProcessingAmount() - remainder);
            //if amount of inserted sets decreased and all outputs are at that state now, then one full set is done
            if (previousSets > matchingOutput.getCurrentlyProcessingSetsCount() &&
                    this.outputs.stream().allMatch(o -> o.getCurrentlyProcessingSetsCount() == previousSets - 1)) {
                this.amountNeeded--;
                //subtract one full set from each input
                for (Input input : this.inputs)
                    input.setProcessingAmount(input.getProcessingAmount() - input.getQuantityPerCraft());
            }

            //distribute to parents
            if(!this.getParents().isEmpty()) {
                stack.amount = (int) remainder;
                //loop through all parents while there is anything left to split up
                for (Iterator<Task> iterator = this.getParents().iterator(); stack.amount > 0 && iterator.hasNext(); ) {
                    Task parent = iterator.next();
                    remainder = parent.supplyInput(stack);
                    //remove if there's nothing left
                    if (remainder == -1) {
                        return 0;
                    } else {
                        stack.amount = (int) remainder;
                    }
                }
            }
        }

        return remainder < 0 ? 0 : (int) remainder;
    }

    /**
     * Merges the given {@code input} into the given {@code list}.
     *
     * @param input the {@link Input} the should be merged
     * @param list  the list in which the given {@code input} should be merged into
     */
    private void mergeIntoList(Input input, List<Input> list) {
        boolean merged = false;
        for (Input output : list) {
            if (input.equals(output)) {
                output.merge(input);
                merged = true;
            }
        }

        if (!merged)
            list.add(input);
    }

    /**
     * Calculates everything about this {@link Task} and creates new sub tasks if they're needed.
     * This function operates recursively.
     *
     * @param network              the network in which the calculation is run
     * @param infiniteInputs       a list of already seen {@link ItemStack}s which have been detected as infinite. This
     *                             list is checked to make sure infinite items are not extracted multiple times
     * @param calculationTimeStart the timestamp of when the calculation initially started
     * @return the {@link CalculationResult}
     */
    @Nonnull
    public CalculationResult calculate(@Nonnull INetwork network, @Nonnull List<ItemStack> infiniteInputs,
                                       @Nonnull IStackList<ItemStack> recursedItemStacks,
                                       @Nonnull IStackList<FluidStack> recursedFluidStacks, long calculationTimeStart) {
        //return if calculation takes too long
        if (System.currentTimeMillis() - calculationTimeStart > RS.INSTANCE.config.calculationTimeoutMs)
            return new CalculationResult(new CraftingTaskError(CraftingTaskErrorType.TOO_COMPLEX));

        CalculationResult result = new CalculationResult();

        inputLoop:
        for (Input input : this.inputs) {
            //handle infinite inputs
            if (input instanceof InfiniteInput) {
                boolean exists = false;

                for (ItemStack infiniteInput : infiniteInputs) {
                    //input already has been handled
                    if (API.instance().getComparer().isEqual(infiniteInput, input.getCompareableItemStack())) {
                        //force set the count because it already exists
                        input.increaseItemStackAmount(input.getCompareableItemStack(), 1);
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    infiniteInputs.add(input.getCompareableItemStack());
                }
            }

            //skip the input if it's already satisfied
            if (input.getAmountMissing() < 1)
                continue;

            //first search for missing amount in network
            if (!input.isFluid()) { //extract items
                if (input instanceof DurabilityInput) { //handle durability inputs
                    DurabilityInput durabilityInput = (DurabilityInput) input;
                    //always only extract 1 item
                    ItemStack extracted = network.extractItem(durabilityInput.getCompareableItemStack(), 1,
                            IComparer.COMPARE_NBT, Action.PERFORM);

                    //extract as many items as needed
                    while (!extracted.isEmpty()) {
                        durabilityInput.addDamageableItemStack(extracted);

                        //keep extracting if input is not satisfied
                        if (input.getAmountMissing() > 0)
                            extracted = network.extractItem(durabilityInput.getCompareableItemStack(), 1,
                                    IComparer.COMPARE_NBT, Action.PERFORM);
                        else
                            break;
                    }
                } else { //handle normal inputs
                    for (ItemStack ingredient : input.getItemStacks()) {
                        //TODO: support inserting and extracting of more than Integer.MAX_VALUE xd
                        ItemStack extracted = network.extractItem(ingredient,
                                input.getAmountMissing() > Integer.MAX_VALUE ? Integer.MAX_VALUE :
                                        (int) input.getAmountMissing(), Action.PERFORM);
                        if (extracted.isEmpty())
                            continue;

                        long remainder = input.increaseItemStackAmount(extracted, extracted.getCount());
                        //special case for infinite inputs -> tell this input that it is the one that actually extracted
                        //an item
                        if (input instanceof InfiniteInput) {
                            ((InfiniteInput) input).setContainsItem(true);
                        }

                        //if it extracted too much, insert it back. Shouldn't happen
                        if (remainder != -1) {
                            if (remainder != 0)
                                network.insertItem(ingredient, (int) remainder, Action.PERFORM);
                            continue inputLoop;
                        }
                    }
                }
            } else { //extract fluid
                //TODO: support inserting and extracting of more than Integer.MAX_VALUE
                FluidStack extracted = network.extractFluid(input.getFluidStack(),
                        input.getAmountMissing() > Integer.MAX_VALUE ? Integer.MAX_VALUE :
                                (int) input.getAmountMissing(), Action.PERFORM);
                if (extracted != null) {
                    long remainder = input.increaseFluidStackAmount(extracted.amount);
                    //if it extracted too much, insert it back. Shouldn't happen
                    if (remainder != -1) {
                        if (remainder != 0)
                            network.insertFluid(input.getFluidStack(), (int) remainder, Action.PERFORM);
                        continue;
                    }
                }
            }

            //if input is not satisfied -> search for patterns to craft this input
            if (input.getAmountMissing() > 0) {

                //find pattern to craft more
                ICraftingPattern pattern;
                if (!input.isFluid())
                    //TODO: add possibility for oredict components to be crafted
                    pattern = getPattern(network.getCraftingManager(), input.getCompareableItemStack());
                else
                    pattern = getPattern(network.getCraftingManager(), input.getFluidStack());

                //add new sub task if pattern is valid
                if (pattern != null && pattern.isValid()) {
                    //if any of the given inputs are in the recursion lists, then this pattern cannot be used
                    if (input.isFluid() ? recursedFluidStacks.get(input.getFluidStack()) == null :
                            recursedItemStacks.get(input.getCompareableItemStack()) == null) {

                        Task newTask;
                        if (pattern.isProcessing())
                            newTask = new ProcessingTask(pattern, input.getAmountMissing(), input.isFluid());
                        else
                            newTask = new CraftingTask(pattern, input.getAmountMissing());

                        //TODO: use patterns instead of items and fluids for recursion detection
                        IStackList<ItemStack> itemCopy = recursedItemStacks.copy();
                        IStackList<FluidStack> fluidCopy = recursedFluidStacks.copy();
                        if (!input.isFluid())
                            itemCopy.add(input.getCompareableItemStack());
                        else
                            fluidCopy.add(input.getFluidStack());

                        CalculationResult newTaskResult =
                                newTask.calculate(network, infiniteInputs, itemCopy, fluidCopy, calculationTimeStart);
                        //immediately fail if calculation had any error
                        if (newTaskResult.getError() != null)
                            return newTaskResult;

                        //make sure nothing is missing for this input, missing stuff is handled by the child task
                        input.increaseToCraftAmount(input.getAmountMissing());

                        newTask.addParent(this);
                        result.getNewTasks().add(newTask);
                        //merge the calculation results
                        result.merge(newTaskResult);
                    }
                }
            }

            //if input cannot be satisfied -> add to missing
            if (input.getAmountMissing() > 0) {

                if (!input.isFluid()) { //missing itemstacks
                    ItemStack missing = input.getCompareableItemStack().copy();
                    //avoid int overflow
                    //TODO: add support for real ItemStack counts
                    if (input.getAmountMissing() > Integer.MAX_VALUE)
                        missing.setCount(Integer.MAX_VALUE);
                    else
                        missing.setCount((int) input.getAmountMissing());
                    result.getMissingItemStacks().add(missing);
                } else { //missing fluid stacks
                    FluidStack missing = input.getFluidStack();

                    //TODO: add support for real FluidStack counts
                    //avoid overflow
                    if (input.getAmountMissing() > Integer.MAX_VALUE)
                        missing.amount = Integer.MAX_VALUE;
                    else
                        missing.amount = (int) input.getAmountMissing();
                    result.getMissingFluidStacks().add(missing);
                }
            }
        }

        return result;
    }

    /**
     * Copy of {@link com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager#getPattern(ItemStack)} but the
     * pattern of this task is ignored.
     *
     * @param stack           the stack to get a pattern for
     * @param craftingManager the crafting manager
     * @return a corresponding pattern or null if none was found
     */
    @Nullable
    private ICraftingPattern getPattern(@Nonnull ICraftingManager craftingManager, @Nonnull ItemStack stack) {
        for (ICraftingPattern patternInList : craftingManager.getPatterns()) {
            for (ItemStack output : patternInList.getOutputs()) {
                if (API.instance().getComparer().isEqualNoQuantity(output, stack) &&
                        !patternInList.equals(this.pattern) &&
                        patternInList.getBlacklistedItems().stream()
                                .noneMatch(f -> API.instance().getComparer().isEqualNoQuantity(f, stack))) {
                    return patternInList;
                }
            }
        }

        return null;
    }

    /**
     * Copy of {@link com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager#getPattern(FluidStack)} but the
     * pattern of this task is ignored.
     *
     * @param stack           the stack to get a pattern for
     * @param craftingManager the crafting manager
     * @return a corresponding pattern or null if none was found
     */
    @Nullable
    private ICraftingPattern getPattern(@Nonnull ICraftingManager craftingManager, @Nonnull FluidStack stack) {
        for (ICraftingPattern patternInList : craftingManager.getPatterns()) {
            for (FluidStack output : patternInList.getFluidOutputs()) {
                if (API.instance().getComparer().isEqual(output, stack, IComparer.COMPARE_NBT) &&
                        !patternInList.equals(this.pattern) &&
                        patternInList.getBlacklistedFluids().stream().noneMatch(f -> API.instance().getComparer()
                                .isEqual(f, stack, IComparer.COMPARE_NBT))) {
                    return patternInList;
                }
            }
        }

        return null;
    }

    public void addParent(Task task) {
        this.parents.add(task);
    }

    public ICraftingPattern getPattern() {
        return pattern;
    }

    public List<Task> getParents() {
        return parents;
    }

    public List<Input> getInputs() {
        return inputs;
    }

    public List<Output> getOutputs() {
        return outputs;
    }

    public long getAmountNeeded() {
        return amountNeeded;
    }
}
