package com.raoulvdberge.refinedstorage.inventory.fluid;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.function.IntConsumer;

public class FluidInventory {
    private static final String NBT_SLOT = "Slot_%d";

    private final FluidStack[] fluids;
    private final int maxAmount;
    private boolean empty = true;

    @Nullable
    protected IntConsumer listener;

    public FluidInventory(int size, int maxAmount, @Nullable IntConsumer listener) {
        this.fluids = new FluidStack[size];
        this.maxAmount = maxAmount;
        this.listener = listener;
    }

    public FluidInventory(int size, @Nullable IntConsumer listener) {
        this(size, Integer.MAX_VALUE, listener);
    }

    public FluidInventory(int size) {
        this(size, Integer.MAX_VALUE, null);
    }

    public int getSlots() {
        return fluids.length;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public FluidStack[] getFluids() {
        return fluids;
    }

    @Nullable
    public FluidStack getFluid(int slot) {
        return fluids[slot];
    }

    public void setFluid(int slot, @Nullable FluidStack stack) {
        if (stack != null && stack.amount <= 0 && stack.amount > maxAmount) {
            throw new IllegalArgumentException("Fluid size is invalid (given: " + stack.amount + ", max size: " + maxAmount + ")");
        }

        fluids[slot] = stack;

        if (listener != null) {
            listener.accept(slot);
        }

        updateEmptyState();
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();

        for (int i = 0; i < getSlots(); ++i) {
            FluidStack stack = getFluid(i);

            if (stack != null) {
                tag.setTag(String.format(NBT_SLOT, i), stack.writeToNBT(new NBTTagCompound()));
            }
        }

        return tag;
    }

    public void readFromNbt(NBTTagCompound tag) {
        for (int i = 0; i < getSlots(); ++i) {
            String key = String.format(NBT_SLOT, i);

            if (tag.hasKey(key)) {
                fluids[i] = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(key));
            }
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        this.empty = true;

        for (FluidStack fluid : fluids) {
            if (fluid != null) {
                this.empty = false;

                return;
            }
        }
    }

    public boolean isEmpty() {
        return empty;
    }
}
