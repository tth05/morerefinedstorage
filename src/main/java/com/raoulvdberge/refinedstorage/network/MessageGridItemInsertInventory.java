package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageGridItemInsertInventory extends MessageHandlerPlayerToServer<MessageGridItemInsertInventory>
        implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    protected void handle(MessageGridItemInsertInventory message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (!(container instanceof ContainerGrid))
            return;

        IGrid grid = ((ContainerGrid) container).getGrid();

        if (grid.getGridType() == GridType.FLUID || grid.getItemHandler() == null)
            return;

        //transfer whole inventory
        for (int i = 9; i < 36; i++) {
            player.inventory.setInventorySlotContents(i, grid.getItemHandler()
                    .onShiftClick(player, player.inventory.getStackInSlot(i)));
        }

        ItemStack inHand = player.inventory.getItemStack();
        if (!inHand.isEmpty()) {
            player.inventory.setItemStack(grid.getItemHandler().onShiftClick(player, inHand));
            player.updateHeldItem();
        }
    }
}
