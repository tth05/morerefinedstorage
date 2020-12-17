package com.raoulvdberge.refinedstorage.container.transfer;

import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.tile.config.RSTileConfiguration;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.function.Supplier;

class InventoryWrapperFilter implements IInventoryWrapper {
    private final InventoryWrapperFilterItem item;
    private final InventoryWrapperFilterFluid fluid;
    private final Supplier<RSTileConfiguration.FilterType> typeGetter;

    InventoryWrapperFilter(IItemHandlerModifiable itemTo, FluidInventory fluidTo, Supplier<RSTileConfiguration.FilterType> typeGetter) {
        this.item = new InventoryWrapperFilterItem(itemTo);
        this.fluid = new InventoryWrapperFilterFluid(fluidTo);
        this.typeGetter = typeGetter;
    }

    @Override
    public InsertionResult insert(ItemStack stack) {
        return typeGetter.get() == RSTileConfiguration.FilterType.ITEMS ? item.insert(stack) : fluid.insert(stack);
    }
}
