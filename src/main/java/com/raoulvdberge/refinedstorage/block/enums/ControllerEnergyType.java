package com.raoulvdberge.refinedstorage.block.enums;

import net.minecraft.util.IStringSerializable;

import javax.annotation.Nonnull;

public enum ControllerEnergyType implements IStringSerializable {
    OFF(0, "off"),
    NEARLY_OFF(1, "nearly_off"),
    NEARLY_ON(2, "nearly_on"),
    ON(3, "on");

    private final int id;
    private final String name;

    ControllerEnergyType(int id, String name) {
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

    public static ControllerEnergyType getById(int id) {
        for (ControllerEnergyType type : values()) {
            if (type.id == id) {
                return type;
            }
        }

        return OFF;
    }
}
