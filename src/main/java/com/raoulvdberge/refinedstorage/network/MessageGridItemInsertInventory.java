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

    private boolean hotbar;

    public MessageGridItemInsertInventory() {
    }

    public MessageGridItemInsertInventory(boolean hotbar) {
        this.hotbar = hotbar;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.hotbar = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.hotbar);
    }

    @Override
    protected void handle(MessageGridItemInsertInventory message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (!(container instanceof ContainerGrid))
            return;

        IGrid grid = ((ContainerGrid) container).getGrid();

        if (!grid.isActive() || grid.getGridType() == GridType.FLUID || grid.getItemHandler() == null)
            return;

        int index, bound;
        if(message.hotbar) {
            index = 0;
            bound = 9;
        } else {
            index = 9;
            bound = 36;
        }

        //transfer whole inventory or just hotbar
        for (; index < bound; index++) {
            player.inventory.setInventorySlotContents(index, grid.getItemHandler()
                    .onShiftClick(player, player.inventory.getStackInSlot(index)));
        }

        ItemStack inHand = player.inventory.getItemStack();
        if (!inHand.isEmpty()) {
            player.inventory.setItemStack(grid.getItemHandler().onShiftClick(player, inHand));
            player.updateHeldItem();
        }
    }
}
