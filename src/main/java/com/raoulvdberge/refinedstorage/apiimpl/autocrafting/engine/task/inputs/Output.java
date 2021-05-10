package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

/**
 * Represents an Output of a Task
 */
public class Output extends Input {

    private static final String NBT_COMPLETED_SETS = "CompletedSets";

    private long completedSets;

    public Output(@Nonnull ItemStack itemStack, int quantityPerCraft) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), 0);
        //amount needed is not relevant for outputs, but quantity is
        this.quantityPerCraft = quantityPerCraft;
    }

    public Output(@Nonnull FluidStack fluidStack, int quantityPerCraft) {
        super(fluidStack, 0);
        //amount needed is not relevant for outputs, but quantity is
        this.quantityPerCraft = quantityPerCraft;
    }

    public Output(@Nonnull NBTTagCompound compound) throws CraftingTaskReadException {
        super(compound);
        this.completedSets = compound.getLong(NBT_COMPLETED_SETS);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        super.writeToNbt(compound);
        compound.setLong(NBT_COMPLETED_SETS, this.completedSets);
        return compound;
    }

    public void setCompletedSets(long completedSets) {
        this.completedSets = completedSets;
    }

    /**
     * @return the amount of sets that are completed
     */
    public long getCompletedSets() {
        return this.completedSets;
    }

    /**
     * @return the amount of sets yet to be completed
     */
    public long getMissingSets() {
        return ((long) Math.ceil(this.amountNeeded / (double) this.quantityPerCraft) - getCompletedSets());
    }
}
