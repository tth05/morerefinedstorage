package com.raoulvdberge.refinedstorage.gui.grid.sorting;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class GridSorterID implements IGridSorter {
    @Override
    public boolean isApplicable(IGrid grid) {
        return grid.getSortingType() == IGrid.SORTING_TYPE_ID;
    }

    @Override
    public int compare(IGridStack left, IGridStack right, GridSorterDirection sortingDirection) {
        int leftId = 0;
        int rightId = 0;

        if (left.getIngredient() instanceof ItemStack && right.getIngredient() instanceof ItemStack) {
            leftId = Item.getIdFromItem(((ItemStack) left.getIngredient()).getItem());
            rightId = Item.getIdFromItem(((ItemStack) right.getIngredient()).getItem());
        }

        if (leftId != rightId) {
            if (sortingDirection == GridSorterDirection.DESCENDING) {
                return Integer.compare(leftId, rightId);
            } else if (sortingDirection == GridSorterDirection.ASCENDING) {
                return Integer.compare(rightId, leftId);
            }
        }

        return 0;
    }
}