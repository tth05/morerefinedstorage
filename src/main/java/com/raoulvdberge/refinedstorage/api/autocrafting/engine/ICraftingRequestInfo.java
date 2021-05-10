package com.raoulvdberge.refinedstorage.api.autocrafting.engine;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Contains information about a crafting request.
 */
public interface ICraftingRequestInfo {
    /**
     * @return the item requested, or null if a fluid was requested
     */
    @Nullable
    ItemStack getItem();

    /**
     * @return the fluid requested, or null if an item was requested
     */
    @Nullable
    FluidStack getFluid();

    /**
     * @return the requested quantity
     */
    long getQuantity();

    /**
     * @return the written tag
     */
    NBTTagCompound writeToNbt();
}
