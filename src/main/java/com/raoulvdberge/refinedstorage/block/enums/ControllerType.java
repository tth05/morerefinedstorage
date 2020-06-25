package com.raoulvdberge.refinedstorage.block.enums;

import net.minecraft.util.IStringSerializable;

import javax.annotation.Nonnull;

public enum ControllerType implements IStringSerializable {
    NORMAL(0, "normal"),
    CREATIVE(1, "creative");

    private final int id;
    private final String name;

    ControllerType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Nonnull
    @Override
    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return name;
    }
}
