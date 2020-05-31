package com.raoulvdberge.refinedstorage.block.enums;

import net.minecraft.util.IStringSerializable;

import javax.annotation.Nonnull;

public enum PortableGridDiskState implements IStringSerializable {
    NORMAL(0, "normal"),
    NEAR_CAPACITY(1, "near_capacity"),
    FULL(2, "full"),
    DISCONNECTED(3, "disconnected"),
    NONE(4, "none");

    private final int id;
    private final String type;

    PortableGridDiskState(int id, String type) {
        this.id = id;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    @Nonnull
    @Override
    public String getName() {
        return type;
    }

    @Override
    public String toString() {
        return type;
    }

    public static PortableGridDiskState getById(int id) {
        for (PortableGridDiskState diskState : values()) {
            if (diskState.getId() == id) {
                return diskState;
            }
        }

        return NONE;
    }
}

