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
    private List<ItemStack> itemStacks = NonNullList.create();
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
