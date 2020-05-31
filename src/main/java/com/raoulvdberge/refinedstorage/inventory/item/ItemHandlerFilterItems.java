package com.raoulvdberge.refinedstorage.inventory.item;

import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemStackHandler;

public class ItemHandlerFilterItems extends ItemStackHandler {
    private final ItemStack stack;

    public ItemHandlerFilterItems(ItemStack stack) {
        super(27);

        this.stack = stack;

        if (stack.hasTagCompound()) {
            StackUtils.readItems(this, 0, stack.getTagCompound());
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);

        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }

        StackUtils.writeItems(this, 0, stack.getTagCompound());
    }

    public NonNullList<ItemStack> getFilteredItems() {
        return stacks;
    }
}
