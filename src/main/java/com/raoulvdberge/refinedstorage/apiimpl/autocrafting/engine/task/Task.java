package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class Task {

    protected final List<Task> parents = new ObjectArrayList<>();
    protected final List<Input> inputs = new ObjectArrayList<>();
    protected final List<Output> outputs = new ObjectArrayList<>();

    protected final ICraftingPattern pattern;

    public Task(ICraftingPattern pattern) {
        this.pattern = pattern;
    }

    protected boolean valid;

    public abstract void update();
    public abstract CalculationResult calculate();

    public void addParent(Task task) {
        this.parents.add(task);
    }

    public boolean isValid() {
        return valid;
    }

    public ICraftingPattern getPattern() {
        return pattern;
    }

    public List<Task> getParents() {
        return parents;
    }

    public List<Input> getInputs() {
        return inputs;
    }

    public List<Output> getOutputs() {
        return outputs;
    }

    protected static class Output extends Input {
        public Output(@Nonnull ItemStack itemStack, long amountNeeded, int quantityPerCraft) {
            super(NonNullList.from(ItemStack.EMPTY, itemStack),
                    (int) Math.ceil((double) amountNeeded / (double) quantityPerCraft), quantityPerCraft, false);
        }

        public Output(@Nonnull FluidStack fluidStack, long amountNeeded, int quantityPerCraft) {
            super(fluidStack, (int) Math.ceil((double) amountNeeded / (double) quantityPerCraft), quantityPerCraft, false);
        }
    }

    protected static class Input {
        private List<ItemStack> itemStacks;
        private FluidStack fluidStack;

        private long currentAmount;
        private final long amountNeeded;
        private final int quantityPerCraft;

        private boolean oredict;

        private Input(long amount, int quantityPerCraft, boolean oredict) {
            this.amountNeeded = amount;
            this.quantityPerCraft = quantityPerCraft;
            this.oredict = oredict;
        }

        public Input(@Nonnull NonNullList<ItemStack> itemStacks, long amountNeed, int quantityPerCraft, boolean oredict) {
            this(amountNeed, quantityPerCraft, oredict);
            this.itemStacks = itemStacks;
        }

        public Input(@Nonnull FluidStack fluidStack, long amountNeeded, int quantityPerCraft, boolean oredict) {
            this(amountNeeded, quantityPerCraft, oredict);
            this.fluidStack = fluidStack;
        }

        public void increaseAmount(long amount) {
            //TODO: increase amount for correct items so the correct items can be given back if task fails
            this.currentAmount += amount;
        }

        public boolean isFluid() {
            return fluidStack != null;
        }

        public long getAmountNeeded() {
            return amountNeeded;
        }

        public FluidStack getFluidStack() {
            return fluidStack;
        }

        public List<ItemStack> getItemStacks() {
            return itemStacks;
        }
    }
}
