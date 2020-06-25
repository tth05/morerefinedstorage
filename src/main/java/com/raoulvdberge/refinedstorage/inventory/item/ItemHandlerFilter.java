package com.raoulvdberge.refinedstorage.inventory.item;

import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridTab;
import com.raoulvdberge.refinedstorage.api.util.IFilter;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.GridTab;
import com.raoulvdberge.refinedstorage.apiimpl.util.FilterFluid;
import com.raoulvdberge.refinedstorage.apiimpl.util.FilterItem;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventoryFilter;
import com.raoulvdberge.refinedstorage.inventory.item.validator.ItemValidatorBasic;
import com.raoulvdberge.refinedstorage.item.ItemFilter;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ItemHandlerFilter extends ItemHandlerBase {
    private final List<IFilter<?>> filters;
    private final List<IGridTab> tabs;

    public ItemHandlerFilter(List<IFilter<?>> filters, List<IGridTab> tabs, @Nullable IntConsumer listener) {
        super(4, listener, new ItemValidatorBasic(RSItems.FILTER));

        this.filters = filters;
        this.tabs = tabs;
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);

        filters.clear();
        tabs.clear();

        for (int i = 0; i < getSlots(); ++i) {
            ItemStack filter = getStackInSlot(i);

            if (!filter.isEmpty()) {
                addFilter(filter);
            }
        }

        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            GuiBase.executeLater(GuiGrid.class, grid -> grid.getView().sort());
        }
    }

    private void addFilter(ItemStack filter) {
        int compare = ItemFilter.getCompare(filter);
        int mode = ItemFilter.getMode(filter);
        boolean modFilter = ItemFilter.isModFilter(filter);

        List<IFilter<?>> filterList = new ArrayList<>();

        ItemHandlerFilterItems items = new ItemHandlerFilterItems(filter);

        for (ItemStack stack : items.getFilteredItems()) {
            if (stack.getItem() == RSItems.FILTER) {
                addFilter(stack);
            } else if (!stack.isEmpty()) {
                filterList.add(new FilterItem(stack, compare, mode, modFilter));
            }
        }

        FluidInventoryFilter fluids = new FluidInventoryFilter(filter);

        for (FluidStack stack : fluids.getFilteredFluids()) {
            filterList.add(new FilterFluid(stack, compare, mode, modFilter));
        }

        ItemStack icon = ItemFilter.getIcon(filter);
        FluidStack fluidIcon = ItemFilter.getFluidIcon(filter);

        if (icon.isEmpty() && fluidIcon == null) {
            this.filters.addAll(filterList);
        } else {
            tabs.add(new GridTab(filterList, ItemFilter.getName(filter), icon, fluidIcon));
        }
    }
}
