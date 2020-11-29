package com.raoulvdberge.refinedstorage.tile.config;

import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.wrapper.InvWrapper;

public interface IUpgradeContainer {

    static void readFromNBT(IUpgradeContainer container, EntityPlayer placer, NBTTagCompound tag) {
        ItemHandlerUpgrade handler = container.getUpgradeHandler();

        InvWrapper inventory = new InvWrapper(placer.inventory);

        for (int i = 0; i < handler.getSlots(); i++) {
            if (!tag.hasKey(i + "") || !handler.getStackInSlot(i).isEmpty())
                continue;

            ItemStack upgradeStack = new ItemStack(tag.getCompoundTag(i + ""));
            for (int j = 0; j < inventory.getSlots(); j++) {
                if(!API.instance().getComparer().isEqualNoQuantity(upgradeStack, inventory.getStackInSlot(j)))
                    continue;

                ItemStack extracted = inventory.extractItem(j, 1, false);
                if (!extracted.isEmpty()) {
                    handler.insertItem(i, extracted, false);
                    break;
                }
            }
        }
    }

    static NBTTagCompound writeToNBT(IUpgradeContainer container, NBTTagCompound tag) {
        ItemHandlerUpgrade handler = container.getUpgradeHandler();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty())
                continue;
            tag.setTag(i + "", stack.writeToNBT(new NBTTagCompound()));
        }
        return tag;
    }

    ItemHandlerUpgrade getUpgradeHandler();
}
