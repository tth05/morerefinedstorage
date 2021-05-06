package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingTaskError;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public abstract class Task {

    public static final String NBT_TASK_TYPE = "TaskType";
    public static final String NBT_PARENT_UUIDS = "ParentUuids";

    private static final String NBT_PATTERN = "Pattern";
    private static final String NBT_PATTERN_STACK = "PatternStack";
    private static final String NBT_AMOUNT_NEEDED = "AmountNeeded";
    private static final String NBT_INPUTS = "Inputs";
    private static final String NBT_OUTPUTS = "Outputs";
    private static final String NBT_UUID = "Uuid";

    protected final List<Task> parents = new ObjectArrayList<>();
    protected final List<Input> inputs = new ObjectArrayList<>();
    protected final List<Output> outputs = new ObjectArrayList<>();

    protected final ICraftingPattern pattern;
    protected long amountNeeded;

    private UUID uuid = UUID.randomUUID();

    public Task(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        this.pattern = pattern;

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
                            newInput = new InfiniteInput(itemStack);
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
                            newInput = new DurabilityInput(itemStack, amountNeeded);
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
                            newInput = new RestockableInput(inputItemStack, inputItemStack.getCount());
                        }
                    }
                }
            }

            //if it's not a durability or infinite input, then just use a normal input
            if (newInput == null)
                newInput = new Input(itemStacks, amountNeeded);

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern fluid inputs
        for (FluidStack i : pattern.getFluidInputs()) {
            Input newInput = new Input(i, amountNeeded);

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

        //need later when minimum stack size is calculated
        List<Output> ignoredOutputs = new ObjectArrayList<>();

        //decrease output amounts if restockable input exists
        for (Input input : this.inputs) {
            if (!(input instanceof RestockableInput))
                continue;

            for (Output output : this.outputs) {
                if (API.instance().getComparer()
                        .isEqualNoQuantity(output.getCompareableItemStack(), input.getCompareableItemStack())) {
                    ignoredOutputs.add(output);

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
                                        1),
                                this.inputs);
                    }

                    break;
                }
            }
        }

        //find smallest output counts
        int smallestOutputStackSize = Integer.MAX_VALUE;
        int smallestOutputFluidStackSize = Integer.MAX_VALUE;

        for (Output output : this.outputs) {
            if (output.getQuantityPerCraft() < 1 || ignoredOutputs.contains(output))
                continue;

            if (!output.isFluid()) {
                smallestOutputStackSize = Math.min(smallestOutputStackSize, output.getQuantityPerCraft());
            } else {
                smallestOutputFluidStackSize = Math.min(smallestOutputFluidStackSize, output.getQuantityPerCraft());
            }
        }

        //calculate actual needed amount, basically the amount of iterations that have to be run
        this.amountNeeded = (long) Math.ceil((double) amountNeeded / (double) (isFluidRequested ?
                smallestOutputFluidStackSize : smallestOutputStackSize));

        //set correct amount for all inputs
        for (Input input : this.inputs) {
            input.setAmountNeeded(this.amountNeeded * input.getQuantityPerCraft());
        }

        //set correct processing amounts for all outputs
        for (Output output : this.outputs) {
            int qpc = output.getQuantityPerCraft();
            output.setProcessingAmount(this.amountNeeded * Math.max(qpc, 1));
            output.setAmountNeeded(output.getProcessingAmount());
        }
    }

    public Task(@Nonnull INetwork network, @Nonnull NBTTagCompound compound) throws CraftingTaskReadException {
        this.uuid = compound.getUniqueId(NBT_UUID);
        this.pattern = readPatternFromNbt(compound.getCompoundTag(NBT_PATTERN), network.world());
        this.amountNeeded = compound.getLong(NBT_AMOUNT_NEEDED);

        NBTTagList inputs = compound.getTagList(NBT_INPUTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < inputs.tagCount(); i++) {
            Input newInput;
            //determine correct input type
            NBTTagCompound inputTag = inputs.getCompoundTagAt(i);
            String inputType = inputTag.getString(Input.NBT_INPUT_TYPE);

            switch (inputType) {
                case Input.TYPE:
                    newInput = new Input(inputTag);
                    break;
                case RestockableInput.TYPE:
                    newInput = new RestockableInput(inputTag);
                    break;
                case InfiniteInput.TYPE:
                    newInput = new InfiniteInput(inputTag);
                    break;
                case DurabilityInput.TYPE:
                    newInput = new DurabilityInput(inputTag);
                    break;
                default:
                    throw new CraftingTaskReadException("Unknown input type: " + inputType);
            }

            this.inputs.add(newInput);
        }

        NBTTagList outputs = compound.getTagList(NBT_OUTPUTS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < outputs.tagCount(); i++) {
            this.outputs.add(new Output(outputs.getCompoundTagAt(i)));
        }
    }

    private NBTTagCompound writePatternToNbt(ICraftingPattern pattern) {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setTag(NBT_PATTERN_STACK, pattern.getStack().serializeNBT());

        return tag;
    }

    private ICraftingPattern readPatternFromNbt(NBTTagCompound tag, World world) throws CraftingTaskReadException {
        ItemStack stack = new ItemStack(tag.getCompoundTag(NBT_PATTERN_STACK));

        if (stack.getItem() instanceof ICraftingPatternProvider) {
            return ((ICraftingPatternProvider) stack.getItem()).create(world, stack, null);
        } else {
            throw new CraftingTaskReadException("Pattern stack is not a crafting pattern provider: " + stack);
        }
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
     * @return any loose item stacks that should not be voided if the task completes
     */
    @Nonnull
    public abstract List<ItemStack> getLooseItemStacks();

    /**
     * @return any loose fluid stacks that should not be voided if the task completes
     */
    @Nonnull
    public List<FluidStack> getLooseFluidStacks() {
        return Collections.emptyList();
    }

    /**
     * @return the type of the task
     */
    @Nonnull
    public abstract String getTaskType();

    /**
     * This also replaces {@code ProcessingState.PROCESSED}
     *
     * @return whether or not this task is finished
     */
    public abstract boolean isFinished();

    /**
     * Supplies an input to this task. Called by sub tasks when they crafted something.
     *
     * @param stack the stack to supply (the count of this stack is modified)
     */
    protected void supplyInput(ItemStack stack) {
        if (!isFinished()) {
            //give to all inputs while there's anything left
            for (Input input : this.inputs) {
                input.decreaseToCraftAmount(stack);

                //no remainder left -> just return
                if (stack.isEmpty())
                    return;
            }
        }
    }

    /**
     * Supplies an input to this task. Called when a tracked fluid is imported. Also forwards imported fluids to parents
     * if this is a processing task.
     *
     * @param stack the stack to supply (the count of this stack is modified)
     */
    protected void supplyInput(FluidStack stack) {
        if (!isFinished()) {
            //give to all inputs while there's anything left
            for (Input input : this.inputs) {
                input.decreaseToCraftAmount(stack);

                //no remainder left -> just return
                if (stack.amount < 1)
                    return;
            }
        }
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
                                       @Nonnull HashSet<ICraftingPattern> recursedPatterns, long calculationTimeStart) {
        //return if calculation takes too long
        if (System.currentTimeMillis() - calculationTimeStart > RS.INSTANCE.config.calculationTimeoutMs)
            return new CalculationResult(new CraftingTaskError());

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
                    StackListResult<ItemStack> extracted = network.extractItem(durabilityInput.getCompareableItemStack(), 1L,
                            IComparer.COMPARE_NBT, Action.PERFORM);

                    //extract as many items as needed
                    while (extracted != null) {
                        durabilityInput.addDamageableItemStack(extracted.getFixedStack());

                        //keep extracting if input is not satisfied
                        if (input.getAmountMissing() > 0)
                            extracted = network.extractItem(durabilityInput.getCompareableItemStack(), 1L,
                                    IComparer.COMPARE_NBT, Action.PERFORM);
                        else
                            break;
                    }
                } else { //handle normal inputs
                    for (ItemStack ingredient : input.getItemStacks()) {

                        StackListResult<ItemStack> extracted = network.extractItem(ingredient,
                                input.getAmountMissing(), Action.PERFORM);

                        if (extracted == null)
                            continue;

                        long remainder = input.increaseItemStackAmount(ingredient, extracted.getCount());
                        //special case for infinite inputs -> tell this input that it is the one that actually extracted
                        //an item
                        if (input instanceof InfiniteInput) {
                            ((InfiniteInput) input).setContainsItem(true);
                        }

                        //if it extracted too much, insert it back. Shouldn't happen
                        if (remainder != -1) {
                            if (remainder != 0)
                                network.insertItem(ingredient, remainder, Action.PERFORM);
                            continue inputLoop;
                        }
                    }
                }
            } else { //extract fluid
                StackListResult<FluidStack> extracted = network.extractFluid(input.getFluidStack(),
                        input.getAmountMissing(), Action.PERFORM);

                if (extracted != null) {
                    long remainder = input.increaseFluidStackAmount(extracted.getCount());
                    //if it extracted too much, insert it back. Shouldn't happen
                    if (remainder != -1) {
                        if (remainder != 0)
                            network.insertFluid(input.getFluidStack(), remainder, Action.PERFORM);
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
                    pattern = network.getCraftingManager().getPattern(input.getCompareableItemStack(), p -> !p.equals(this.pattern));
                else
                    pattern = network.getCraftingManager().getPattern(input.getFluidStack(), p -> !p.equals(this.pattern));

                //add new sub task if pattern is valid and is not used recursively
                if (pattern != null && pattern.isValid() && !recursedPatterns.contains(pattern)) {
                    Task newTask;
                    if (pattern.isProcessing())
                        newTask = new ProcessingTask(pattern, input.getAmountMissing(), input.isFluid());
                    else
                        newTask = new CraftingTask(pattern, input.getAmountMissing());

                    HashSet<ICraftingPattern> recursedPatternsCopy = (HashSet<ICraftingPattern>) recursedPatterns.clone();
                    recursedPatternsCopy.add(pattern);

                    CalculationResult newTaskResult =
                            newTask.calculate(network, infiniteInputs, recursedPatternsCopy, calculationTimeStart);
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

            //if input cannot be satisfied -> add to missing
            if (input.getAmountMissing() > 0) {
                if (!input.isFluid()) { //missing itemstacks
                    ItemStack missing = input.getCompareableItemStack().copy();

                    result.getMissingItemStacks().add(missing, input.getAmountMissing());
                } else { //missing fluid stacks
                    FluidStack missing = input.getFluidStack();

                    result.getMissingFluidStacks().add(missing, input.getAmountMissing());
                }
            }
        }

        return result;
    }

    @Nonnull
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        compound.setUniqueId(NBT_UUID, this.uuid);
        compound.setString(NBT_TASK_TYPE, getTaskType());
        compound.setTag(NBT_PATTERN, writePatternToNbt(this.pattern));
        compound.setLong(NBT_AMOUNT_NEEDED, this.amountNeeded);

        NBTTagList inputs = new NBTTagList();
        for (Input input : this.inputs) {
            NBTTagCompound inputTag = new NBTTagCompound();
            inputTag.setString(Input.NBT_INPUT_TYPE, input.getType());
            inputs.appendTag(input.writeToNbt(inputTag));
        }
        compound.setTag(NBT_INPUTS, inputs);

        NBTTagList outputs = new NBTTagList();
        for (Output output : this.outputs) {
            outputs.appendTag(output.writeToNbt(new NBTTagCompound()));
        }
        compound.setTag(NBT_OUTPUTS, outputs);

        if (!this.parents.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (Task parent : this.parents) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setUniqueId(NBT_UUID, parent.getUuid());
                list.appendTag(tag);
            }

            compound.setTag(NBT_PARENT_UUIDS, list);
        }

        return compound;
    }

    /**
     * @return the unique id of this task
     */
    @Nonnull
    public UUID getUuid() {
        return this.uuid;
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
