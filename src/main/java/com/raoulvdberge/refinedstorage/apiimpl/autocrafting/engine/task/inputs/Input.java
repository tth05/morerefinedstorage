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

    /**
     * The possible ItemStacks that are allowed for this input. Only contains multiple entries when using oredict.
     */
    private final List<ItemStack> itemStacks = NonNullList.create();
    /**
     * The FluidStack used for this input, if this Input is a fluid
     */
    private FluidStack fluidStack;

    /**
     * The current amount that this Input contains. Sums up all amounts from different oredict ItemStacks, that's
     * why this is separate.
     */
    protected long totalInputAmount;
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
    private final boolean oredict;

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
     * @param amount the amount
     * @return the remaining amount; or {@code -1} if the given {@code amount} does not satisfy this input
     */
    public long increaseItemStackAmount(ItemStack stack, long amount) {
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
     * @param stack the item that was crafted
     * @param amount the amount that was crafted
     * @return the amount of the given item that remains; {@code -1} if the item is not valid; or {@code -2} if there's
     * no remainder
     */
    public long decreaseToCraftAmount(ItemStack stack, long amount) {
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
        if(!found)
            return -1;

        long realAmount = Math.min(toCraftAmount, amount);
        this.totalInputAmount += this.currentInputCounts.get(i) + realAmount;
        this.currentInputCounts.set(i, this.currentInputCounts.get(i) + realAmount);
        return amount - realAmount < 1 ? -2 : amount - realAmount;
    }

    /**
     * Decrease the total input amount and input counts by the given {@code amount} as if they were used up in crafting.
     * @param amount the amount that should be used up
     */
    public void decreaseItemStackAmount(long amount) {
        this.totalInputAmount -= amount;

        List<Long> inputCounts = this.currentInputCounts;
        for (int i = 0; i < inputCounts.size(); i++) {
            Long currentInputCount = inputCounts.get(i);
            if(currentInputCount == 0)
                continue;
            if(amount < 1)
                break;

            currentInputCount -= amount;
            if(currentInputCount < 0) {
                amount = -currentInputCount;
                currentInputCount = 0L;
            }

            inputCounts.set(i, currentInputCount);
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

    public boolean isFluid() {
        return fluidStack != null;
    }

    public boolean isOredict() {
        return oredict;
    }

    @Nonnull
    public ItemStack getCompareableItemStack() {
        if(this.isFluid())
            throw new UnsupportedOperationException("Comparable ItemStack does not exist for fluid inputs!");
        return this.itemStacks.get(0);
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

    public long getMinCraftAmount() {
        long minCraftAmount = totalInputAmount / quantityPerCraft;
        if(minCraftAmount > Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return minCraftAmount;
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
