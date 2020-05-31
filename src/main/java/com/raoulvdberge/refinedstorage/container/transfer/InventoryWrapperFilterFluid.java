package com.raoulvdberge.refinedstorage.container.transfer;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

class InventoryWrapperFilterFluid implements IInventoryWrapper {
    private final FluidInventory filterInv;

    InventoryWrapperFilterFluid(FluidInventory filterInv) {
        this.filterInv = filterInv;
    }

    @Override
    public InsertionResult insert(ItemStack stack) {
        InsertionResult stop = new InsertionResult(InsertionResultType.STOP);

        FluidStack fluidInContainer = StackUtils.getFluid(stack, true).getValue();

        if (fluidInContainer == null) {
            return stop;
        }

        for (FluidStack fluid : filterInv.getFluids()) {
            if (API.instance().getComparer().isEqual(fluidInContainer, fluid, IComparer.COMPARE_NBT)) {
                return stop;
            }
        }

        for (int i = 0; i < filterInv.getSlots(); ++i) {
            if (filterInv.getFluid(i) == null) {
                filterInv.setFluid(i, StackUtils.copy(fluidInContainer, Fluid.BUCKET_VOLUME));

                return stop;
            }
        }

        return stop;
    }
}
