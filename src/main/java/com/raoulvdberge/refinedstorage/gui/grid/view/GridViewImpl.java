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
import java.util.stream.Stream;

public class GridViewImpl implements IGridView {
    private final GuiGrid gui;
    private boolean canCraft;

    private final IGridSorter defaultSorter;
    private final List<IGridSorter> sorters;

    private List<IGridStack> stacks = new ArrayList<>();
    protected final Map<UUID, IGridStack> map = new HashMap<>();

    public GridViewImpl(GuiGrid gui, IGridSorter defaultSorter, List<IGridSorter> sorters) {
        this.gui = gui;
        this.defaultSorter = defaultSorter;
        this.sorters = sorters;
    }

    @Override
    public void sort() {
        if (gui.getGrid().isActive()) {
            Predicate<IGridStack> activeFilters = getActiveFilters();

            this.stacks = new ObjectArrayList<>(map.values());

            this.stacks.removeIf(gridStack -> {
                if (gui.getGrid().getViewType() != IGrid.VIEW_TYPE_CRAFTABLES &&
                        gridStack.isCraftable() &&
                        gridStack.getOtherId() != null &&
                        map.containsKey(gridStack.getOtherId()))
                    return true;

                return !activeFilters.test(gridStack);
            });

            this.stacks.sort(getActiveSort());
        } else {
            this.stacks = Collections.emptyList();
        }

        this.gui.updateScrollbar();
    }

    @Override
    public void postChange(IGridStack stack, long delta) {
        /*
        !!!!!!!!!!!!!!!!!!!
        otherId for normal GridStack is the id of the same GridStack but the craftable version
        otherId for craftable GridStack is the id of the GridStack but the normal version

        A GridStack that contains a proper count will never be craftable. In this case a second GridStack always exists.
        !!!!!!!!!!!!!!!!!!
         */

        // COMMENT 1 (about this if check in general)
        // Update the other id reference if needed.
        // Taking a stack out - and then re-inserting it - gives the new stack a new ID
        // With that new id, the reference for the crafting stack would be outdated.

        // COMMENT 2 (about map.containsKey(stack.getOtherId()))
        // This check is needed or the .updateOtherId() call will crash with a NPE in high-update environments.
        // This is because we might have scenarios where we process "old" delta packets from another session when we haven't received any initial update packet from the new session.
        // (This is because of the executeLater system)
        // This causes the .updateOtherId() to fail with a NPE because the map is still empty or the IDs mismatch.
        // We could use !map.isEmpty() here too. But if we have 2 "old" delta packets, it would rightfully ignore the first one. But this method mutates the map and would put an entry.
        // This means that on the second delta packet it would still crash because the map wouldn't be empty anymore.
        if (!stack.isCraftable() &&
                stack.getOtherId() != null &&
                map.containsKey(stack.getOtherId())) {
            IGridStack craftingStack = map.get(stack.getOtherId());

            craftingStack.updateOtherId(stack.getId());
            craftingStack.setTrackerEntry(stack.getTrackerEntry());
        }

        IGridStack existing = map.get(stack.getId());
        boolean stillExists = true;

        Predicate<IGridStack> activeFilters = getActiveFilters();

        if (existing == null) {
            stack.setCount(delta);

            map.put(stack.getId(), stack);

            //remove craftable stack from view
            if (!stack.isCraftable() && stack.getOtherId() != null) {
                IGridStack craftingStack = map.get(stack.getOtherId());
                if (craftingStack != null)
                    stacks.remove(craftingStack);
            }

            existing = stack;
        } else {
            stacks.remove(existing);
            existing.grow(delta);
            if (existing.getCount() <= 0) {
                map.remove(existing.getId());

                //add craftable stack to view
                if (!existing.isCraftable() && existing.getOtherId() != null) {
                    IGridStack craftingStack = map.get(stack.getOtherId());
                    if (craftingStack != null && activeFilters.test(craftingStack))
                        binaryInsert(craftingStack);
                }

                stillExists = false;
            }

            existing.setTrackerEntry(stack.getTrackerEntry());
        }

        if (stillExists && activeFilters.test(existing)) {
            binaryInsert(existing);
        }

        this.gui.updateScrollbar();
    }

    private void binaryInsert(IGridStack stack) {
        int insertionPos = Collections.binarySearch(stacks, stack, getActiveSort());
        if (insertionPos < 0) {
            insertionPos = -insertionPos - 1;
        }
        stacks.add(insertionPos, stack);
    }

    @Override
    public void setStacks(List<IGridStack> stacks) {
        map.clear();

        for (IGridStack stack : stacks) {
            map.put(stack.getId(), stack);
        }
    }

    @Override
    public void setCanCraft(boolean canCraft) {
        this.canCraft = canCraft;
    }

    @Override
    public boolean canCraft() {
        return canCraft;
    }

    private Predicate<IGridStack> getActiveFilters() {
        IGrid grid = gui.getGrid();
        return GridFilterParser.getFilters(
                grid,
                gui.getSearchField() != null ? gui.getSearchField().getText() : "",
                (grid.getTabSelected() >= 0 && grid.getTabSelected() < grid.getTabs().size()) ? grid.getTabs().get(grid.getTabSelected()).getFilters() : grid.getFilters()
        );
    }

    private Comparator<IGridStack> getActiveSort() {
        IGrid grid = gui.getGrid();
        GridSorterDirection sortingDirection = grid.getSortingDirection() == IGrid.SORTING_DIRECTION_DESCENDING ? GridSorterDirection.DESCENDING : GridSorterDirection.ASCENDING;

        return Stream.concat(Stream.of(defaultSorter), sorters.stream().filter(s -> s.isApplicable(grid)))
                .map(sorter -> (Comparator<IGridStack>) (o1, o2) -> sorter.compare(o1, o2, sortingDirection))
                .reduce((l, r) -> r.thenComparing(l))
                .orElseThrow(IllegalStateException::new);
    }

    @Nullable
    @Override
    public IGridStack get(UUID id) {
        return map.get(id);
    }

    @Override
    public List<IGridStack> getStacks() {
        return stacks;
    }
}
