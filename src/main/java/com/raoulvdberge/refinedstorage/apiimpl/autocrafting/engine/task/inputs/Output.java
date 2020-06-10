package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

/**
 * Represents an Output of a Task
 */
public class Output extends Input {
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

    /**
     * Adds one full set of this output to the processing amount.
     * <br>
     * Equal to:
     * <pre> {@code
     * output.setProcessingAmount(output.getProcessingAmount() + output.getQuantityPerCraft());
     * }</pre>
     */
    public void scheduleSet() {
        this.processingAmount += this.quantityPerCraft;
    }

    /**
     * @return the amount of sets that are currently being awaited
     */
    public long getCurrentlyProcessingSetsCount() {
        return (long) Math.ceil((double)this.processingAmount / this.quantityPerCraft);
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
}
