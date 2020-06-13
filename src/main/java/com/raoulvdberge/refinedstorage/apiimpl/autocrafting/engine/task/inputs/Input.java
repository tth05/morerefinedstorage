package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Represents an Input of a Task. Can be a fluid or an item
 */
public class Input {

    private final List<ItemStack> itemStacks = NonNullList.create();
    private FluidStack fluidStack;

    protected long totalInputAmount;
    private final List<Long> currentInputCounts = new LongArrayList(9);

    private long toCraftAmount;

    private long amountNeeded;
    protected long processingAmount = 0;
    protected int quantityPerCraft;

    private Input(long amount) {
        this.amountNeeded = amount;
    }

    public Input(@Nonnull NonNullList<ItemStack> itemStacks, long amountNeeded) {
        this(amountNeeded);
        itemStacks.forEach(i -> this.itemStacks.add(i.copy()));
        this.quantityPerCraft = itemStacks.get(0).getCount();

        this.itemStacks.forEach(i -> currentInputCounts.add(0L));
    }

    public Input(@Nonnull FluidStack fluidStack, long amountNeeded, boolean oredict) {
        this(amountNeeded);
        this.fluidStack = fluidStack.copy();
        this.quantityPerCraft = fluidStack.amount;

        this.currentInputCounts.add(0L);
    }

    /**
     * Adds one full set of this output to the processing amount.
     * <br>
     * Equal to:
     * <pre> {@code
     * this.processingAmount = this.processingAmount + (this.quantityPerCraft * amount);
     * }</pre>
     */
    public void scheduleSets(int amount) {
        this.processingAmount += this.quantityPerCraft * amount;
    }

    /**
     * Increases the given {@code amount} for the given {@code stack}
     *
     * @param amount the amount
     * @return the remaining amount; or {@code -1} if the given {@code amount} does not satisfy this input
     */
    public long increaseItemStackAmount(@Nonnull ItemStack stack, long amount) {
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
     * @param amount the amount
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
     * Decreases the to craft amount for this input and increase the total input amount. This is used to supply this
     * inputs with newly crafted items.
     *
     * @param stack  the item that was crafted (the count of this stack is modified)
     */
    public void decreaseToCraftAmount(@Nonnull ItemStack stack) {
        int i = 0;
        boolean found = false;

        List<ItemStack> stacks = this.itemStacks;
        for (; i < stacks.size(); i++) {
            ItemStack itemStack = stacks.get(i);
            if (API.instance().getComparer().isEqualNoQuantity(stack, itemStack)) {
                found = true;
                break;
            }
        }

        //these return code aren't optimal but better than making another method that checks just this
        if (!found)
            return;

        //adjust all values accordingly
        long realAmount = Math.min(toCraftAmount, stack.getCount());
        this.toCraftAmount = Math.max(toCraftAmount - stack.getCount(), 0);
        this.totalInputAmount += realAmount;
        this.currentInputCounts.set(i, this.currentInputCounts.get(i) + realAmount);
        stack.setCount((int) (stack.getCount() - realAmount));
    }

    /**
     * Decreases the to craft amount for this input and increase the total input amount. This is used to supply this
     * inputs with newly crafted fluids.
     *
     * @param stack the fluid that was crafted (the count of this stack is modified)
     */
    public void decreaseToCraftAmount(@Nonnull FluidStack stack) {
        //these return code aren't optimal but better than making another method that checks just this
        if (!API.instance().getComparer().isEqual(stack, this.getFluidStack(), IComparer.COMPARE_NBT))
            return;

        //adjust all values accordingly
        long realAmount = Math.min(toCraftAmount, stack.amount);
        this.toCraftAmount = Math.max(toCraftAmount - stack.amount, 0);
        this.totalInputAmount += realAmount;
        this.currentInputCounts.set(0, this.currentInputCounts.get(0) + realAmount);
        stack.amount -= realAmount;
    }

    /**
     * Decrease the total input amount and input counts by the given {@code amount} as if they were used up in crafting.
     *
     * @param amount the amount that should be used up
     */
    public void decreaseInputAmount(long amount) {
        this.totalInputAmount -= amount;

        if(isFluid()) {
            //only decrease the amount from first count
            Long currentInputCount = this.currentInputCounts.get(0);
            currentInputCount -= amount;
            this.currentInputCounts.set(0, currentInputCount < 0 ? 0 : currentInputCount);
        } else {
            //decrease the amount from all input counts
            List<Long> inputCounts = this.currentInputCounts;
            for (int i = 0; i < inputCounts.size(); i++) {
                Long currentInputCount = inputCounts.get(i);
                if (currentInputCount == 0)
                    continue;
                if (amount < 1)
                    break;

                currentInputCount -= amount;
                if (currentInputCount < 0) {
                    amount = -currentInputCount;
                    currentInputCount = 0L;
                } else {
                    amount = 0;
                }

                inputCounts.set(i, currentInputCount);
            }
        }
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

    public void setProcessingAmount(long processingAmount) {
        this.processingAmount = Math.max(processingAmount, 0);
    }

    /**
     * @return whether or not this input represents a fluid
     */
    public boolean isFluid() {
        return fluidStack != null;
    }

    @Nonnull
    public ItemStack getCompareableItemStack() {
        if (this.isFluid())
            throw new UnsupportedOperationException("Comparable ItemStack does not exist for fluid inputs!");
        return this.itemStacks.get(0);
    }

    /**
     * @return the total amount that is needed of this Input
     */
    public long getAmountNeeded() {
        return amountNeeded;
    }

    /**
     * Only relevant for processing tasks and not used during calculation
     * @return the amount of this input that is currently being processed
     */
    public long getProcessingAmount() {
        return processingAmount;
    }

    /**
     * @return the current amount that this Input contains. Sums up all amounts from different oredict ItemStacks, that's
     * why this is separate.
     */
    public long getTotalInputAmount() {
        return totalInputAmount;
    }

    /**
     * @return the amount that is expected to be crafted
     */
    public long getToCraftAmount() {
        return toCraftAmount;
    }

    /**
     * @return the total amount that this input is still missing
     */
    public long getAmountMissing() {
        return Math.max(amountNeeded - totalInputAmount - toCraftAmount, 0);
    }

    /**
     * @return the maximum count of crafting iterations that this input can do
     */
    public long getMinimumCraftableAmount() {
        long minCraftAmount = totalInputAmount / quantityPerCraft;
        if (minCraftAmount > Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return minCraftAmount;
    }

    /**
     * @return the current input counts for all possibilities of this Input. Only contains multiple entries if oredict
     * is used, because each oredict possibility can have a different amount. This information is needed, so that the
     * correct items can be later inserted back into the network (if the task gets cancelled).
     */
    public List<Long> getCurrentInputCounts() {
        return currentInputCounts;
    }

    /**
     * @return the FluidStack used for this input, if this Input is a fluid
     */
    public FluidStack getFluidStack() {
        return fluidStack;
    }

    /**
     * @return the possible ItemStacks that are allowed for this input. Only contains multiple entries when using oredict.
     */
    public List<ItemStack> getItemStacks() {
        return itemStacks;
    }

    /**
     * @return how much of this Input is used per crafting operation
     */
    public int getQuantityPerCraft() {
        return quantityPerCraft;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Input input = (Input) o;

        if (this.itemStacks.size() != input.getItemStacks().size() ||
                (this.fluidStack == null) == (input.getFluidStack() != null))
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
