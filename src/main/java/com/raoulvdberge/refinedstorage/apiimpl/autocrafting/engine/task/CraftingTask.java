package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementFluidRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementItemRender;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.DurabilityInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.InfiniteInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a crafting task
 */
public class CraftingTask extends Task {

    public static final String TYPE = "crafting";

    private static final String NBT_REMAINDER_ITEM = "RemainderItem";

    /**
     * Saves the remainder from previous crafting attempts to make sure this doesn't get lost
     */
    @Nullable
    private StackListResult<ItemStack> remainder = null;

    @Nullable
    private List<ItemStack> byProducts;

    private boolean finished = false;

    public CraftingTask(@Nonnull ICraftingPattern pattern, long amountNeeded) {
        super(pattern, amountNeeded, false);

        if (pattern.isProcessing())
            throw new IllegalArgumentException("Processing pattern cannot be used for crafting task!");

        generateCleanByProducts();
    }

    public CraftingTask(@Nonnull INetwork network, @Nonnull NBTTagCompound compound)
            throws CraftingTaskReadException {
        super(network, compound);
        if (this.amountNeeded < 1) {
            this.finished = true;
        }

        if (compound.hasKey(NBT_REMAINDER_ITEM)) {
            ItemStack itemStack = new ItemStack(compound.getCompoundTag(NBT_REMAINDER_ITEM));
            this.remainder = new StackListResult<>(itemStack, itemStack.getCount());
        }

        generateCleanByProducts();
    }

    /**
     * Gets the by-products of the current pattern and removes all items which belong to infinite or durability inputs.
     * Assigns the result to the {@code byProducts} variable.
     */
    public void generateCleanByProducts() {
        List<ItemStack> byproducts = new ArrayList<>(this.pattern.getByproducts());
        //clean by-products
        byproducts.removeIf(i -> i.isEmpty() || this.getInputs().stream()
                //filter inputs
                .filter(input -> input instanceof InfiniteInput || input instanceof DurabilityInput)
                //find lists which contain the items of the filtered inputs
                .map(input -> this.getPattern().getInputs().stream()
                        .filter(possibilities -> possibilities.stream()
                                .anyMatch(possibility -> API.instance().getComparer()
                                        //don't compare damage for durability inputs
                                        .isEqual(possibility, input.getCompareableItemStack(), IComparer.COMPARE_NBT |
                                                (input.getCompareableItemStack().isItemStackDamageable() ?
                                                        0 : IComparer.COMPARE_DAMAGE))))
                        .findFirst().orElse(null)
                ).filter(Objects::nonNull)
                //flat map all items
                .flatMap(Collection::stream)
                //find match -> don't compare damage for durability inputs
                .anyMatch(itemStack -> API.instance().getComparer().isEqual(i, itemStack,
                        IComparer.COMPARE_NBT | (itemStack.isItemStackDamageable() ? 0 : IComparer.COMPARE_DAMAGE)))
        );

        this.byProducts = byproducts;
    }

    @Override
    public int update(@Nonnull INetwork network, @Nonnull ICraftingPatternContainer container, int toCraft) {
        //don't update if there's any remainder left from the previous update
        if (this.remainder != null) {
            this.remainder = network.insertItem(this.remainder.getStack(), this.remainder.getCount(), Action.PERFORM);
            return 0;
        }

        //stop if task is finished
        if (this.amountNeeded < 1) {
            this.finished = true;
            return 0;
        }

        //adjust the craftable amount
        if (toCraft > this.amountNeeded)
            toCraft = (int) this.amountNeeded;

        for (Input input : this.inputs) {
            toCraft = (int) Math.min(input.getMinimumCraftableAmount(), toCraft);
        }

        if (toCraft < 1)
            return 0;

        //notify inputs
        for (Input input : this.inputs) {
            input.decreaseInputAmount((long) toCraft * input.getQuantityPerCraft());
        }

        //generate output item
        Output output = this.outputs.get(0);
        ItemStack crafted = ItemHandlerHelper
                .copyStackWithSize(output.getCompareableItemStack(), output.getQuantityPerCraft() * toCraft);

        //insert remainder
        if (this.byProducts != null && !this.byProducts.isEmpty()) {
            for (ItemStack byproduct : this.byProducts)
                network.insertItem(byproduct, toCraft * byproduct.getCount(), Action.PERFORM);
        }

        //give to parents
        if (!this.getParents().isEmpty()) {
            //loop through all parents while there is anything left to split up

            for (Iterator<Task> iterator = this.getParents().iterator(); !crafted.isEmpty() && iterator.hasNext(); ) {
                iterator.next().supplyInput(crafted);
            }
        }

        //if there is to much crafted we insert it back into the network
        if (!crafted.isEmpty())
            this.remainder = network.insertItem(crafted, (long) crafted.getCount(), Action.PERFORM);

        this.amountNeeded -= toCraft;

        //if there's no remainder and the task has crafted everything, we're done
        if (this.amountNeeded < 1 && this.remainder == null)
            this.finished = true;

        network.getCraftingManager().onTaskChanged();
        return toCraft;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        super.writeToNbt(compound);
        if (remainder != null) {
            compound.setTag(NBT_REMAINDER_ITEM, this.remainder.getFixedStack().writeToNBT(new NBTTagCompound()));
        }

        return compound;
    }

    @Nonnull
    @Override
    public List<ICraftingMonitorElement> getCraftingMonitorElements() {
        if (isFinished())
            return Collections.emptyList();

        List<ICraftingMonitorElement> elements = new ObjectArrayList<>(this.inputs.size());
        for (Input input : this.inputs) {
            if (input instanceof InfiniteInput && !((InfiniteInput) input).containsItem())
                continue;

            if (input.isFluid()) {
                elements.add(
                        new CraftingMonitorElementFluidRender(input.getFluidStack(),
                                input.getTotalInputAmount(), 0, 0, input.getToCraftAmount()));
            } else {
                elements.add(
                        new CraftingMonitorElementItemRender(input.getCompareableItemStack(),
                                input.getTotalInputAmount(), 0, 0, input.getToCraftAmount()));
            }
        }


        return elements;
    }

    @Nonnull
    @Override
    public List<ItemStack> getLooseItemStacks() {
        if (this.remainder != null) {
            return Collections.singletonList(remainder.getFixedStack());
        }
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public String getTaskType() {
        return TYPE;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }
}
