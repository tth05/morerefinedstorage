package com.raoulvdberge.refinedstorage.gui.grid.view;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterParser;
import com.raoulvdberge.refinedstorage.gui.grid.sorting.GridSorterDirection;
import com.raoulvdberge.refinedstorage.gui.grid.sorting.IGridSorter;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public abstract class GridViewBase implements IGridView {
    private final GuiGrid gui;
    private boolean canCraft;

    private final IGridSorter defaultSorter;
    private final List<IGridSorter> sorters;

    private List<IGridStack> stacks = new ArrayList<>();
    protected final Map<UUID, IGridStack> map = new HashMap<>();

    public GridViewBase(GuiGrid gui, IGridSorter defaultSorter, List<IGridSorter> sorters) {
        this.gui = gui;
        this.defaultSorter = defaultSorter;
        this.sorters = sorters;
    }

    @Override
    public List<IGridStack> getStacks() {
        return stacks;
    }

    @Nullable
    @Override
    public IGridStack get(UUID id) {
        return map.get(id);
    }

    @Override
    public void sort() {
        List<IGridStack> gridStacks = new ObjectArrayList<>();

        if (gui.getGrid().isActive()) {
            gridStacks.addAll(map.values());

            IGrid grid = gui.getGrid();

            List<Predicate<IGridStack>> filters = GridFilterParser.getFilters(
                    grid,
                    gui.getSearchField() != null ? gui.getSearchField().getText() : "",
                    (grid.getTabSelected() >= 0 && grid.getTabSelected() < grid.getTabs().size()) ?
                            grid.getTabs().get(grid.getTabSelected()).getFilters() : grid.getFilters()
            );

            gridStacks.removeIf(stack -> {

                // If this is a crafting stack,
                // and there is a regular matching stack in the view too,
                // and we aren't in "view only craftables" mode,
                // we don't want the duplicate stacks and we will remove this stack.
                if (gui.getGrid().getViewType() != IGrid.VIEW_TYPE_CRAFTABLES &&
                        stack.isCraftable() &&
                        stack.getOtherId() != null &&
                        map.containsKey(stack.getOtherId())) {
                    return true;
                }

                for (Predicate<IGridStack> filter : filters) {
                    if (!filter.test(stack)) {
                        return true;
                    }
                }
                return false;
            });

            GridSorterDirection sortingDirection =
                    grid.getSortingDirection() == IGrid.SORTING_DIRECTION_DESCENDING ? GridSorterDirection.DESCENDING :
                            GridSorterDirection.ASCENDING;

            gridStacks.sort((left, right) -> defaultSorter.compare(left, right, sortingDirection));

            for (IGridSorter sorter : sorters) {
                if (sorter.isApplicable(grid)) {
                    gridStacks.sort((left, right) -> sorter.compare(left, right, sortingDirection));
                }
            }
        }

        this.stacks = gridStacks;

        this.gui.updateScrollbar();
    }

    @Override
    public void setCanCraft(boolean canCraft) {
        this.canCraft = canCraft;
    }

    @Override
    public boolean canCraft() {
        return canCraft;
    }
}
