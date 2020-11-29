package com.raoulvdberge.refinedstorage.tile.config;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.tileentity.TileEntity;

public interface IComparable {
    static <T extends TileEntity & INetworkNodeProxy> TileDataParameter<Integer, T> createParameter() {
        return new TileDataParameter<>(DataSerializers.VARINT, 0, t -> ((IComparable) t.getNode()).getCompare(), (t, v) -> ((IComparable) t.getNode()).setCompare(v));
    }

    static void readFromNBT(IComparable comparable, NBTTagCompound tag) {
        comparable.setCompare(tag.getInteger("compare"));
    }

    static NBTTagCompound writeToNBT(IComparable comparable, NBTTagCompound tag) {
        tag.setInteger("compare", comparable.getCompare());
        return tag;
    }

    int getCompare();

    void setCompare(int compare);
}
