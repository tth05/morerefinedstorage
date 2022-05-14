package com.raoulvdberge.refinedstorage.tile.config;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public interface IRSFilterConfigProvider {

    @Nonnull
    FilterConfig getConfig();

    default NBTTagCompound writeExtraNbt(NBTTagCompound tag) {
        return tag;
    }

    default void readExtraNbt(NBTTagCompound tag) {
        //NO OP
    }

}
