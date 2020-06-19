package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an Input that is infinite and only needs to be extracted once. Only allowed for crafting tasks.
 */
public class InfiniteInput extends Input {

    public static final String TYPE = "infinite";
    private static final String NBT_CONTAINS_ITEM = "ContainsItem";

    private boolean containsItem;

    public InfiniteInput(@Nonnull ItemStack itemStack, boolean oredict) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), 1);
    }

    public InfiniteInput(@Nonnull NBTTagCompound compound) throws CraftingTaskReadException {
        super(compound);
        this.containsItem = compound.hasKey(NBT_CONTAINS_ITEM);
    }

    @Override
    public void decreaseInputAmount(long amount) {
        //NO OP
    }

    @Override
    public void decreaseToCraftAmount(@Nonnull ItemStack stack) {
        super.decreaseToCraftAmount(stack);
        //if a sub tasks gives an item to an infinite input, then this infinite inputs actually contains something
        this.setContainsItem(true);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        super.writeToNbt(compound);
        if (this.containsItem)
            compound.setBoolean(NBT_CONTAINS_ITEM, true);
        return compound;
    }

    @Nonnull
    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public long getAmountNeeded() {
        return 1;
    }

    @Override
    public void setAmountNeeded(long amountNeeded) {
        //NO OP
    }

    @Override
    public int getQuantityPerCraft() {
        return 0;
    }

    @Override
    public long getMinimumCraftableAmount() {
        return Long.MAX_VALUE;
    }

    /**
     * Whether or not this infinite input actually contains any item. If there are multiple infinite inputs for the
     * same item, then this will only be true for one of them.
     */
    public boolean containsItem() {
        return containsItem;
    }

    public void setContainsItem(boolean containsItem) {
        this.containsItem = containsItem;
    }
}
