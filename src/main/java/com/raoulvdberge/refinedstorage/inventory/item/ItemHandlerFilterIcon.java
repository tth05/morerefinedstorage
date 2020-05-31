package com.raoulvdberge.refinedstorage.inventory.item;

import com.raoulvdberge.refinedstorage.item.ItemFilter;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class ItemHandlerFilterIcon extends ItemStackHandler {
    private final ItemStack stack;

    public ItemHandlerFilterIcon(ItemStack stack) {
        super(1);

        this.stack = stack;

        setStackInSlot(0, ItemFilter.getIcon(stack));
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);

        ItemFilter.setIcon(stack, getStackInSlot(0));
    }
}
