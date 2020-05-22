package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
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

    public Task(ICraftingPattern pattern, long amountNeeded) {
        this.pattern = pattern;
        this.amountNeeded = amountNeeded;
    }

    protected boolean valid;

    public abstract void update();

    @Nonnull
    public abstract CalculationResult calculate(@Nonnull INetwork network);

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
        private List<ItemStack> itemStacks;
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
            this.itemStacks = itemStacks;
            this.quantityPerCraft = itemStacks.get(0).getCount();

            this.itemStacks.forEach(i -> currentInputCounts.add(0L));
        }

        public Input(@Nonnull FluidStack fluidStack, long amountNeeded, boolean oredict) {
            this(amountNeeded, oredict);
            this.fluidStack = fluidStack;
            this.quantityPerCraft = fluidStack.amount;

            this.currentInputCounts.add(0L);
        }

        /**
         * Increases the given {@code amount} for the given {@code stack}
         * @param amount
         * @return the remaining amount; or {@code -1} if the given {@code amount} does not satisfy this input
         */
        public long increaseAmount(ItemStack stack, long amount) {
            long needed = amountNeeded - totalInputAmount;
            long returns = totalInputAmount + amount - amountNeeded;

            this.totalInputAmount = returns < 0 ? this.totalInputAmount + amount : amountNeeded;

            for (int i = 0; i < this.itemStacks.size(); i++) {
                if(API.instance().getComparer().isEqualNoQuantity(stack, this.itemStacks.get(i))) {
                    long currentCount = 0;
                    if(i < this.currentInputCounts.size()) {
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
         * @param amount
         * @return the remaining amount; or {@code -1} if the given {@code amount} does not satisfy this input
         */
        public long increaseFluidStackAmount(long amount) {
            long needed = amountNeeded - totalInputAmount;
            if(amount <= needed) {
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
         * @param amount the amount to add
         */
        public void increaseToCraftAmount(long amount) {
            this.toCraftAmount += amount;
        }

        /**
         * Merges two Inputs. Assumes both inputs are equal
         * @param input the input that should be merged
         */
        public void merge(Input input) {
            if(input.isFluid()) {
                if(!this.isFluid())
                    this.fluidStack = input.getFluidStack();
                else
                    this.fluidStack.amount += input.getFluidStack().amount;

                long oldQuantity = this.quantityPerCraft;

                this.quantityPerCraft = this.fluidStack.amount;
                //recalculate new needed amount for this input
                this.amountNeeded = this.amountNeeded / oldQuantity * quantityPerCraft;
            } else {
                long oldQuantity = this.quantityPerCraft;

                this.quantityPerCraft += input.getQuantityPerCraft();
                this.itemStacks.forEach(i -> i.setCount(this.quantityPerCraft));
                //recalculate new needed amount for this input
                this.amountNeeded = this.amountNeeded / oldQuantity * this.quantityPerCraft;
            }
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
                if(!API.instance().getComparer().isEqualNoQuantity(this.itemStacks.get(i), input.getItemStacks().get(i)))
                    return false;
            }

            return this.fluidStack == null || FluidStack.areFluidStackTagsEqual(this.fluidStack, input.getFluidStack());
        }
    }
}
