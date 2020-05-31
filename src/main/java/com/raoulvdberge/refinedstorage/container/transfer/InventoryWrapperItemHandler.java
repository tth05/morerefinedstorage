package com.raoulvdberge.refinedstorage.container.transfer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Objects;

class InventoryWrapperItemHandler implements IInventoryWrapper {
    private final IItemHandler handler;

    InventoryWrapperItemHandler(IItemHandler handler) {
        this.handler = handler;
    }

    @Override
    public InsertionResult insert(ItemStack stack) {
        return new InsertionResult(ItemHandlerHelper.insertItem(handler, stack, false));
    }

    public IItemHandler getHandler() {
        return handler;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InventoryWrapperItemHandler that = (InventoryWrapperItemHandler) o;

        return Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handler);
    }
}
