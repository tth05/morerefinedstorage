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
}
