package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageGridItemInventoryScroll extends MessageHandlerPlayerToServer<MessageGridItemInventoryScroll>
        implements IMessage {

    private int slot;
    private boolean up;

    public MessageGridItemInventoryScroll() {
    }

    public MessageGridItemInventoryScroll(int slot, boolean up) {
        this.slot = slot;
        this.up = up;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        up = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeBoolean(up);
    }

    @Override
    protected void handle(MessageGridItemInventoryScroll message, EntityPlayerMP player) {
        if (player == null)
            return;
        Container container = player.openContainer;

        if (!(container instanceof ContainerGrid))
            return;
        IGrid grid = ((ContainerGrid) container).getGrid();

        if (grid.getItemHandler() == null)
            return;

        int flags = ItemGridHandler.EXTRACT_SINGLE | ItemGridHandler.EXTRACT_SHIFT;
        ItemStack stackInSlot = player.inventory.getStackInSlot(message.slot);

        if (message.up) { // scroll up
            player.inventory.setInventorySlotContents(message.slot,
                    StackUtils.nullToEmpty(grid.getItemHandler().onInsert(player, stackInSlot, true)));
        } else { // scroll down
            grid.getItemHandler().onExtract(player, stackInSlot, message.slot, flags);
        }
    }
}
