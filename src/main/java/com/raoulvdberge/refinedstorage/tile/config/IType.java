package com.raoulvdberge.refinedstorage.tile.config;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameterClientListener;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;

public interface IType {
    int ITEMS = 0;
    int FLUIDS = 1;

    static <T extends TileEntity & INetworkNodeProxy> TileDataParameter<Integer, T> createParameter(@Nullable TileDataParameterClientListener<Integer> clientListener) {
        return new TileDataParameter<>(DataSerializers.VARINT, ITEMS, t -> ((IType) t.getNode()).getType(), (t, v) -> {
            if (v == IType.ITEMS || v == IType.FLUIDS) {
                ((IType) t.getNode()).setType(v);
            }
        }, clientListener);
    }

    static <T extends TileEntity & INetworkNodeProxy> TileDataParameter<Integer, T> createParameter() {
        return createParameter(null);
    }

    static void readFromNBT(IType iType, NBTTagCompound tag) {
        int type = tag.getInteger("type");
        iType.setType(type);
        if (type == IType.ITEMS && tag.hasKey("items")) {
            NBTTagCompound itemTag = tag.getCompoundTag("items");
            IItemHandlerModifiable handler = iType.getItemFilters();
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!itemTag.hasKey(i + ""))
                    continue;

                handler.setStackInSlot(i, new ItemStack(itemTag.getCompoundTag(i + "")));
            }
        } else if (type == IType.ITEMS && tag.hasKey("fluids")) {
            NBTTagCompound itemTag = tag.getCompoundTag("fluids");
            FluidInventory handler = iType.getFluidFilters();
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!itemTag.hasKey(i + ""))
                    continue;

                handler.setFluid(i, FluidStack.loadFluidStackFromNBT(itemTag.getCompoundTag(i + "")));
            }
        }
    }

    static NBTTagCompound writeToNBT(IType iType, NBTTagCompound tag) {
        int type = iType.getType();
        tag.setInteger("type", type);
        if (type == IType.ITEMS) {
            NBTTagCompound itemTag = new NBTTagCompound();
            IItemHandlerModifiable handler = iType.getItemFilters();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty())
                    continue;

                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                itemTag.setTag(i + "", stackTag);
            }

            tag.setTag("items", itemTag);
        } else if (type == IType.FLUIDS) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            FluidInventory handler = iType.getFluidFilters();
            for (int i = 0; i < handler.getSlots(); i++) {
                FluidStack stack = handler.getFluid(i);
                if (stack == null || stack.amount < 1)
                    continue;

                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                fluidTag.setTag(i + "", stackTag);
            }

            tag.setTag("fluids", fluidTag);
        }

        return tag;
    }


    int getType();

    void setType(int type);

    IItemHandlerModifiable getItemFilters();

    FluidInventory getFluidFilters();
}
