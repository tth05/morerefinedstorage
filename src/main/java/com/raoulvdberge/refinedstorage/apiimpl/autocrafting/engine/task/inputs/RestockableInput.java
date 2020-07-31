package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an input which is satisfied with a small amount to start, but then does need to get restocked during
 * crafting. This type of input is not given by a sub task that produces this input but rather by tracked items which
 * get inserted.
 *
 * This input type is only allowed for Processing Tasks!
 */
public class RestockableInput extends Input {

    public static final String TYPE = "restockable";

    public RestockableInput(@Nonnull ItemStack itemStack, long amountNeeded) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), amountNeeded);
    }

    public RestockableInput(@Nonnull NBTTagCompound compound) throws CraftingTaskReadException {
        super(compound);
    }

    public void fixCounts(int quantityPerCraft) {
        this.quantityPerCraft = quantityPerCraft;
        this.amountNeeded = quantityPerCraft;
    }

    @Override
    public void setAmountNeeded(long amountNeeded) {
        //NO OP
    }

    @Nonnull
    @Override
    public String getType() {
        return TYPE;
    }
}
