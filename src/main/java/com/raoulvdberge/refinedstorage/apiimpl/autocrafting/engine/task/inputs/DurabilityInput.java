package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

/**
 * Represents an item input of a task, where the item uses durability and therefore lasts for much more than one
 * crafting operation.
 */
public class DurabilityInput extends Input {

    /**
     * The max durability of the item that this input represents. Or in other words: the max iterations that can be done
     * with the {@link ItemStack} that represents this input if it had no damage.
     */
    private final int maxDurability;
    private final ItemStack compareableItemStack;

    public DurabilityInput(@Nonnull ItemStack itemStack, long amountNeeded, boolean oredict) {
        super(NonNullList.from(ItemStack.EMPTY, ItemStack.EMPTY), amountNeeded, oredict);
        this.getItemStacks().clear();
        this.compareableItemStack = itemStack;
        this.maxDurability = compareableItemStack.getMaxDamage() + 1;
    }

    private DurabilityInput(@Nonnull FluidStack fluidStack, long amountNeeded, boolean oredict) {
        super(fluidStack, amountNeeded, oredict);
        throw new IllegalArgumentException("FluidStacks are no supported for durability inputs");
    }

    /**
     * Adds the given {@code itemStack} to the list of {@link ItemStack}s of this input. The {@code itemStack} has to
     * damageable and is assumed to be valid for this input. The remaining durability of the {@code itemStack} is then
     * added to the total input count of this input.
     * @param itemStack the {@link ItemStack} to add
     */
    public void addDamageableItemStack(@Nonnull ItemStack itemStack) {
        if(!itemStack.isItemStackDamageable())
            throw new IllegalArgumentException("itemStack has to be damageable!");

        this.getItemStacks().add(itemStack);
        this.getCurrentInputCounts().add((long) (itemStack.getMaxDamage() - itemStack.getItemDamage()) + 1);
        this.totalInputAmount = this.getCurrentInputCounts().stream().mapToLong(l -> l).sum();
    }

    @Override
    public long increaseItemStackAmount(@Nonnull ItemStack stack, long amount) {
        throw new UnsupportedOperationException("#addDamageableItemStack should be used!");
    }

    @Override
    public void merge(Input input) {
        if (input.isFluid()) {
            //durability inputs can't be fluids
            throw new IllegalArgumentException("Other input cannot be a fluid");
        } else {
            this.getItemStacks().addAll(input.getItemStacks());
            this.getCurrentInputCounts().addAll(input.getCurrentInputCounts());

            this.totalInputAmount = this.getCurrentInputCounts().stream().mapToLong(l -> l).sum();
        }
    }

    @Override
    public int getQuantityPerCraft() {
        //always 1
        return 1;
    }

    @Override
    public long getAmountMissing() {
        //returns the amount missing in items, not durability
        long missing = (long) Math.ceil((getAmountNeeded() - totalInputAmount) / (double) maxDurability) -
                getToCraftAmount() * maxDurability;
        return missing < 0 ? 0 : missing;
    }

    public long getTotalItemInputAmount() {
        //divide by durability to get the item count and not the count in durability
        return (long) Math.ceil(this.totalInputAmount / (double)this.maxDurability);
    }

    @Override
    @Nonnull
    public ItemStack getCompareableItemStack() {
        //the ItemStack list may be empty, so we need a separate variable
        return compareableItemStack;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DurabilityInput input = (DurabilityInput) o;

        if (input.isFluid() || input.isOredict())
            return false;

        return API.instance().getComparer().isEqual(this.getCompareableItemStack(), input.getCompareableItemStack(),
                IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY);
    }
}
