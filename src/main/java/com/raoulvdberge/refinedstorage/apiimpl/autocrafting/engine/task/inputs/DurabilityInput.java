package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an item input of a task, where the item uses durability and therefore lasts for much more than one
 * crafting operation.
 */
public class DurabilityInput extends Input {

    public static final String TYPE = "durability";

    private static final String NBT_MAX_DURABILITY = "MaxDurability";
    private static final String NBT_COMPAREABLE_ITEMSTACK = "CompareableItemstack";

    /**
     * The max durability of the item that this input represents. Or in other words: the max iterations that can be done
     * with the {@link ItemStack} that represents this input if it had no damage.
     */
    private final int maxDurability;
    private final ItemStack compareableItemStack;

    public DurabilityInput(@Nonnull ItemStack itemStack, long amountNeeded) {
        super(NonNullList.from(ItemStack.EMPTY, ItemStack.EMPTY), amountNeeded);
        this.getItemStacks().clear();
        this.getCurrentInputCounts().clear();
        this.compareableItemStack = itemStack;
        this.quantityPerCraft = 1;
        this.maxDurability = compareableItemStack.getMaxDamage() + 1;
    }

    public DurabilityInput(@Nonnull NBTTagCompound compound) throws CraftingTaskReadException {
        super(compound);
        this.compareableItemStack = new ItemStack(compound.getCompoundTag(NBT_COMPAREABLE_ITEMSTACK));
        this.maxDurability = compound.getInteger(NBT_MAX_DURABILITY);
    }

    /**
     * Adds the given {@code itemStack} to the list of {@link ItemStack}s of this input. The {@code itemStack} has to
     * damageable and is assumed to be valid for this input. The remaining durability of the {@code itemStack} is then
     * added to the total input count of this input.
     *
     * @param itemStack the {@link ItemStack} to add
     */
    public void addDamageableItemStack(@Nonnull ItemStack itemStack) {
        if (!itemStack.isItemStackDamageable())
            throw new IllegalArgumentException("itemStack has to be damageable!");

        this.getItemStacks().add(itemStack);
        this.getCurrentInputCounts().add((long) (itemStack.getMaxDamage() - itemStack.getItemDamage()) + 1);
        this.totalInputAmount = this.getCurrentInputCounts().stream().mapToLong(l -> l).sum();
    }

    @Override
    public void decreaseToCraftAmount(@Nonnull ItemStack stack) {
        if (!API.instance().getComparer().isEqual(this.compareableItemStack, stack, IComparer.COMPARE_NBT))
            return;

        int needed = (int) Math.min(this.getToCraftAmount(), stack.getCount());

        for (int i = 0; i < needed; i++)
            addDamageableItemStack(stack.copy());

        this.toCraftAmount = Math.max(0, this.toCraftAmount - needed);
        stack.shrink(needed);
    }

    @Override
    public long increaseItemStackAmount(@Nonnull ItemStack stack, long amount) {
        throw new UnsupportedOperationException("#addDamageableItemStack should be used!");
    }

    @Override
    public void merge(Input input) {
        if (input.isFluid()) {
            //durability inputs can't be fluids
            throw new IllegalArgumentException("Other input cannot be a fluid");
        } else {
            this.getItemStacks().addAll(input.getItemStacks());
            this.getCurrentInputCounts().addAll(input.getCurrentInputCounts());

            this.totalInputAmount = this.getCurrentInputCounts().stream().mapToLong(l -> l).sum();
        }
    }

    @Nonnull
    @Override
    public String getType() {
        return TYPE;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNbt(@Nonnull NBTTagCompound compound) {
        super.writeToNbt(compound);
        compound.setInteger(NBT_MAX_DURABILITY, this.maxDurability);
        compound.setTag(NBT_COMPAREABLE_ITEMSTACK, this.compareableItemStack.writeToNBT(new NBTTagCompound()));
        return compound;
    }

    @Override
    public int getQuantityPerCraft() {
        //always 1
        return 1;
    }

    @Override
    public long getAmountMissing() {
        //returns the amount missing in items, not durability
        long missing = (long) Math.ceil((getAmountNeeded() - this.totalInputAmount) / (double) this.maxDurability) -
                getToCraftAmount() * this.maxDurability;
        return missing < 0 ? 0 : missing;
    }

    public long getTotalItemInputAmount() {
        //divide by durability to get the item count and not the count in durability
        return (long) Math.ceil(this.totalInputAmount / (double) this.maxDurability);
    }

    @Override
    public long getMinimumCraftableAmount() {
        return this.totalInputAmount;
    }

    @Override
    @Nonnull
    public ItemStack getCompareableItemStack() {
        //the ItemStack list may be empty, so we need a separate variable
        return this.compareableItemStack;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DurabilityInput input = (DurabilityInput) o;

        if (input.isFluid())
            return false;

        return API.instance().getComparer().isEqual(this.getCompareableItemStack(), input.getCompareableItemStack(),
                IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY);
    }

    @Override
    public int hashCode() {
        int result = maxDurability;
        result = 31 * result +
                (compareableItemStack != null ? API.instance().getItemStackHashCode(compareableItemStack) : 0);
        return result;
    }
}
