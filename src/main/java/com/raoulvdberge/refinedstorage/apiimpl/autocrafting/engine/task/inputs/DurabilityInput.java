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

    private int maxDurability;
    private final ItemStack compareableItemStack;

    public DurabilityInput(@Nonnull ItemStack itemStack, long amountNeeded, boolean oredict) {
        super(NonNullList.from(ItemStack.EMPTY, ItemStack.EMPTY), amountNeeded, oredict);
        this.compareableItemStack = itemStack;
        this.maxDurability = compareableItemStack.getMaxDamage() + 1;
    }

    public DurabilityInput(@Nonnull FluidStack fluidStack, long amountNeeded, boolean oredict) {
        super(fluidStack, amountNeeded, oredict);
        throw new IllegalArgumentException("FluidStacks are no supported for durability inputs");
    }

    public void addDamageableItemStack(@Nonnull ItemStack itemStack) {
        if(!itemStack.isItemStackDamageable())
            throw new IllegalArgumentException("itemStack has to be damageable!");

        this.getItemStacks().add(itemStack);
        this.getCurrentInputCounts().add((long) (itemStack.getMaxDamage() - itemStack.getItemDamage()) + 1);
        this.totalInputAmount = this.getCurrentInputCounts().stream().mapToLong(l -> l).sum();
    }

    @Override
    public long increaseAmount(ItemStack stack, long amount) {
        throw new UnsupportedOperationException("#addDamageableItemStack should be used!");
    }

    @Override
    public void merge(Input input) {
        if (input.isFluid()) {
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

    @Override
    @Nonnull
    public ItemStack getCompareableItemStack() {
        return compareableItemStack;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DurabilityInput input = (DurabilityInput) o;

        if (input.getItemStacks().size() < 1 || input.isFluid() || input.isOredict())
            return false;

        return API.instance().getComparer().isEqual(this.getCompareableItemStack(), input.getCompareableItemStack(),
                IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY);
    }
}
