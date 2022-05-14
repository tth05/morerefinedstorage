package com.raoulvdberge.refinedstorage.tile.config;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public interface IRSFilterConfigProvider {

    @Nonnull
    FilterConfig getConfig();

    NBTTagCompound writeExtraNbt(NBTTagCompound tag);

    void readExtraNbt(NBTTagCompound tag);

}
