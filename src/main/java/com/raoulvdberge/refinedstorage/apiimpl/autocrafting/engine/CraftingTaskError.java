package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine;

import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskErrorType;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;

public class CraftingTaskError implements ICraftingTaskError {
    private final CraftingTaskErrorType type;

    public CraftingTaskError(CraftingTaskErrorType type) {
        this.type = type;
    }

    @Override
    public CraftingTaskErrorType getType() {
        return type;
    }
}
