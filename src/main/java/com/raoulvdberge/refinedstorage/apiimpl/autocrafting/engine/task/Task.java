package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class Task {

    protected final List<Task> parents = new ObjectArrayList<>();
    protected final List<Input> inputs = new ObjectArrayList<>();
    protected final List<Output> outputs = new ObjectArrayList<>();

    protected final ICraftingPattern pattern;
    protected final long amountNeeded;

    public Task(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        //merge all pattern inputs
        for (NonNullList<ItemStack> itemStacks : pattern.getInputs()) {
            if (itemStacks.isEmpty())
                continue;

            Input newInput = new Input(itemStacks, amountNeeded, pattern.isOredict());

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern fluid inputs
        for (FluidStack i : pattern.getFluidInputs()) {
            Input newInput = new Input(i, amountNeeded, pattern.isOredict());

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern outputs
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

        //find smallest output counts
        int smallestOutputStackSize = Integer.MAX_VALUE;
        int smallestOutputFluidStackSize = Integer.MAX_VALUE;

        for (Output output : this.outputs) {
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
        this.valid = true;
        this.amountNeeded = amountNeeded;
    }

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

    @Nonnull
    public CalculationResult calculate(@Nonnull INetwork network) {
        CalculationResult result = new CalculationResult();

        inputLoop:
        for (Input input : this.inputs) {
            //first search for missing amount in network
            if (!input.isFluid()) { //extract items
                for (ItemStack ingredient : input.getItemStacks()) {
                    //TODO: support inserting and extracting of more than Integer.MAX_VALUE xd
                    ItemStack extracted = network.extractItem(ingredient,
                            input.getAmountMissing() > Integer.MAX_VALUE ? Integer.MAX_VALUE :
                                    (int) input.getAmountMissing(), Action.PERFORM);
                    if (extracted.isEmpty())
                        continue;

                    long remainder = input.increaseAmount(extracted, extracted.getCount());
                    //if it extracted too much, insert it back. Shouldn't happen
                    if (remainder != -1) {
                        if (remainder != 0)
                            network.insertItem(ingredient, (int) remainder, Action.PERFORM);
                        continue inputLoop;
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

            //if input is not satisfied
            if (input.getAmountMissing() > 0) {

                //find pattern to craft more
                ICraftingPattern pattern;
                if (!input.isFluid())
                    //TODO: add possibility for oredict components to be crafted
                    pattern = network.getCraftingManager().getPattern(input.getItemStacks().get(0));
                else
                    pattern = network.getCraftingManager().getPattern(input.getFluidStack());

                //add new sub task
                if (pattern != null) {
                    Task newTask;
                    if (pattern.isProcessing())
                        newTask = new ProcessingTask(pattern, input.getAmountMissing(), input.isFluid());
                    else
                        newTask = new CraftingTask(pattern, input.getAmountMissing());
                    newTask.addParent(this);
                    CalculationResult newTaskResult = newTask.calculate(network);

                    //make sure nothing is missing for this input, missing stuff is handled by the child task
                    input.increaseToCraftAmount(input.getAmountMissing());

                    result.addNewTask(newTask);
                    //merge the calculation results
                    result.merge(newTaskResult);
                }
            }

            //if input cannot be satisfied -> add to missing
            if (input.getAmountMissing() > 0) {

                if (!input.isFluid()) { //missing itemstacks
                    ItemStack missing = input.getItemStacks().get(0).copy();
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

    //TODO: is this needed?
    protected boolean valid;

    public abstract void update();

    public void addParent(Task task) {
        this.parents.add(task);
    }

    public boolean isValid() {
        return valid;
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

    /**
     * Represents an Output of a Task
     */
    protected static class Output extends Input {
        public Output(@Nonnull ItemStack itemStack, int quantityPerCraft) {
            super(NonNullList.from(ItemStack.EMPTY, itemStack), 0, false);
            //amount needed is not relevant for outputs, but quantity is
            this.quantityPerCraft = quantityPerCraft;
        }

        public Output(@Nonnull FluidStack fluidStack, int quantityPerCraft) {
            super(fluidStack, 0, false);
            //amount needed is not relevant for outputs, but quantity is
            this.quantityPerCraft = quantityPerCraft;
        }
    }

    /**
     * Represents an Input of a Task
     */
    protected static class Input {

        /**
         * The possible ItemStacks that are allowed for this input. Only contains multiple entries when using oredict.
         */
        private List<ItemStack> itemStacks = NonNullList.create();
        /**
         * The FluidStack used for this input, if this Input is a fluid
         */
        private FluidStack fluidStack;

        /**
         * The current amount that this Input contains. Sums up all amounts from different oredict ItemStacks, that's
         * why this is separate.
         */
        private long totalInputAmount;
        /**
         * The current input counts for all possibilities of this Input. Only contains multiple entries if oredict is
         * used, because each oredict possibility can have a different amount. This information is needed, so that the
         * correct items can be later inserted back into the network (if the task gets cancelled).
         */
        private final List<Long> currentInputCounts = new LongArrayList(9);

        /**
         * The amount that will be crafted
         */
        private long toCraftAmount;

        /**
         * The total amount that is needed of this Input
         */
        private long amountNeeded;
        /**
         * How much of this Input is used per crafting operation
         */
        protected int quantityPerCraft;

        /**
         * Whether or not this Input uses oredict. //TODO: maybe remove
         */
        private boolean oredict;

        private Input(long amount, boolean oredict) {
            this.amountNeeded = amount;
            this.oredict = oredict;
        }

        public Input(@Nonnull NonNullList<ItemStack> itemStacks, long amountNeeded, boolean oredict) {
            this(amountNeeded, oredict);
            itemStacks.forEach(i -> this.itemStacks.add(i.copy()));
            this.quantityPerCraft = itemStacks.get(0).getCount();

            this.itemStacks.forEach(i -> currentInputCounts.add(0L));
        }

        public Input(@Nonnull FluidStack fluidStack, long amountNeeded, boolean oredict) {
            this(amountNeeded, oredict);
            this.fluidStack = fluidStack.copy();
            this.quantityPerCraft = fluidStack.amount;

            this.currentInputCounts.add(0L);
        }

        /**
         * Increases the given {@code amount} for the given {@code stack}
         *
         * @param amount
         * @return the remaining amount; or {@code -1} if the given {@code amount} does not satisfy this input
         */
        public long increaseAmount(ItemStack stack, long amount) {
            long needed = amountNeeded - totalInputAmount;
            long returns = totalInputAmount + amount - amountNeeded;

            this.totalInputAmount = returns < 0 ? this.totalInputAmount + amount : amountNeeded;

            for (int i = 0; i < this.itemStacks.size(); i++) {
                if (API.instance().getComparer().isEqualNoQuantity(stack, this.itemStacks.get(i))) {
                    long currentCount = 0;
                    if (i < this.currentInputCounts.size()) {
                        currentCount = this.currentInputCounts.get(i);
                    }

                    this.currentInputCounts.set(i, currentCount + (returns < 0 ? amount : needed));
                    break;
                }
            }

            return returns < 0 ? -1 : returns;
        }

        /**
         * Increases the given {@code amount} for the current FluidStack
         *
         * @param amount
         * @return the remaining amount; or {@code -1} if the given {@code amount} does not satisfy this input
         */
        public long increaseFluidStackAmount(long amount) {
            long needed = amountNeeded - totalInputAmount;
            if (amount <= needed) {
                this.totalInputAmount += amount;
                this.currentInputCounts.set(0, totalInputAmount);
                return -1;
            } else {
                long returns = totalInputAmount + amount - amountNeeded;
                this.totalInputAmount = amountNeeded;
                this.currentInputCounts.set(0, totalInputAmount);
                return returns;
            }
        }

        /**
         * Increases the amount that is expected to be crafted for this input by the given {@code amount}
         *
         * @param amount the amount to add
         */
        public void increaseToCraftAmount(long amount) {
            this.toCraftAmount += amount;
        }

        /**
         * Merges two Inputs. Assumes both inputs are equal.
         * Does not merge the amount needed of both Inputs.
         *
         * @param input the input that should be merged
         */
        public void merge(Input input) {
            if (input.isFluid()) {
                if (!this.isFluid())
                    this.fluidStack = input.getFluidStack();
                else
                    this.fluidStack.amount += input.getFluidStack().amount;

                this.quantityPerCraft = this.fluidStack.amount;
            } else {
                this.quantityPerCraft += input.getQuantityPerCraft();
            }
        }

        public void setAmountNeeded(long amountNeeded) {
            this.amountNeeded = amountNeeded;
        }

        public boolean isFluid() {
            return fluidStack != null;
        }

        public boolean isOredict() {
            return oredict;
        }

        public long getAmountNeeded() {
            return amountNeeded;
        }

        public long getTotalInputAmount() {
            return totalInputAmount;
        }

        public long getToCraftAmount() {
            return toCraftAmount;
        }

        public long getAmountMissing() {
            return Math.max(amountNeeded - totalInputAmount - toCraftAmount, 0);
        }

        public List<Long> getCurrentInputCounts() {
            return currentInputCounts;
        }

        public FluidStack getFluidStack() {
            return fluidStack;
        }

        public List<ItemStack> getItemStacks() {
            return itemStacks;
        }

        public int getQuantityPerCraft() {
            return quantityPerCraft;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Input input = (Input) o;

            if (this.itemStacks.size() != input.getItemStacks().size() ||
                    (this.fluidStack == null) == (input.getFluidStack() != null) ||
                    oredict != input.isOredict())
                return false;

            for (int i = 0; i < itemStacks.size(); i++) {
                if (!API.instance().getComparer()
                        .isEqualNoQuantity(this.itemStacks.get(i), input.getItemStacks().get(i)))
                    return false;
            }

            return this.fluidStack == null ||
                    API.instance().getComparer().isEqual(this.fluidStack, input.getFluidStack(), IComparer.COMPARE_NBT);
        }
    }
}
