package com.raoulvdberge.refinedstorage.container.transfer;

import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

class InventoryWrapperFilterItem implements IInventoryWrapper {
    private final IItemHandlerModifiable filterInv;

    InventoryWrapperFilterItem(IItemHandlerModifiable filterInv) {
        this.filterInv = filterInv;
    }

    @Override
    public InsertionResult insert(ItemStack stack) {
        InsertionResult stop = new InsertionResult(InsertionResultType.STOP);

        for (int i = 0; i < filterInv.getSlots(); ++i) {
            if (API.instance().getComparer().isEqualNoQuantity(filterInv.getStackInSlot(i), stack)) {
                return stop;
            }
        }

        for (int i = 0; i < filterInv.getSlots(); ++i) {
            if (filterInv.getStackInSlot(i).isEmpty()) {
                filterInv.setStackInSlot(i, ItemHandlerHelper.copyStackWithSize(stack, 1));

                break;
            }
        }

        return stop;
    }
}
