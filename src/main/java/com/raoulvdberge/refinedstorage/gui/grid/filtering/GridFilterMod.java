package com.raoulvdberge.refinedstorage.gui.grid.filtering;

import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;

import java.util.function.Predicate;

public class GridFilterMod implements Predicate<IGridStack> {
    private final String inputModName;

    public GridFilterMod(String inputModName) {
        this.inputModName = standardify(inputModName);
    }

    @Override
    public boolean test(IGridStack stack) {
        String modId = stack.getModId();

        if (modId != null) {
            if (modId.contains(inputModName)) {
                return true;
            }

            String modName = stack.getModName();
            if (modName != null) {
                modName = standardify(modName);

                return modName.contains(inputModName);
            }
        }

        return false;
    }

    private String standardify(String input) {
        return input.toLowerCase().replace(" ", "");
    }
}
