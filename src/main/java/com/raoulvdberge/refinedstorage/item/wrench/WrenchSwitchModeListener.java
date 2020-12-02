package com.raoulvdberge.refinedstorage.item.wrench;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.network.MessageChangeWrenchMode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class WrenchSwitchModeListener {

    @SubscribeEvent
    public void onMouseWheelEvent(MouseEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;

        if (event.getDwheel() == 0 || !player.isSneaking())
            return;

        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemWrench) {
            ItemWrench.addDefaultMode(stack);
            NBTTagCompound tagCompound = stack.getTagCompound();

            WrenchMode currentMode = WrenchMode.valueOf(tagCompound.getString("mode"));
            currentMode = event.getDwheel() < 0 ? currentMode.getNext() : currentMode.getPrevious();

            RS.INSTANCE.network.sendToServer(new MessageChangeWrenchMode(currentMode.ordinal(), player.inventory.currentItem));
            event.setCanceled(true);
        }
    }
}
