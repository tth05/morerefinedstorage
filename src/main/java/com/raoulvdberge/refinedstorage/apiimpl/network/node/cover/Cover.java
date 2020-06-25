package com.raoulvdberge.refinedstorage.apiimpl.network.node.cover;

import net.minecraft.item.ItemStack;

public class Cover {
    private final ItemStack stack;
    private final CoverType type;

    public Cover(ItemStack stack, CoverType type) {
        this.stack = stack;
        this.type = type;
    }

    public ItemStack getStack() {
        return stack;
    }

    public CoverType getType() {
        return type;
    }
}
