package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
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

    /**
     * Applies the given {@code input} to this output. Basically subtracting this outputs QPC with the given QPC.
     * @param input the {@link RestockableInput} to apply
     * @return the amount that is still left of the restockable input because the input QPC is less than the output QPC;
     * 0 otherwise
     */
    public long applyRestockableInput(RestockableInput input) {
        this.quantityPerCraft -= input.getQuantityPerCraft();

        if(this.quantityPerCraft < 0) {
            long returns = this.quantityPerCraft;
            this.quantityPerCraft = 0;
            return -returns;
        }

        return 0;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        NBTTagCompound tag = super.writeToNbt(compound);
        tag.setLong(NBT_COMPLETED_SETS, this.completedSets);
        return tag;
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
}
