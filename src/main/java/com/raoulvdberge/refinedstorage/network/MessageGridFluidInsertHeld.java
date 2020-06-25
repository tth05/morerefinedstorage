package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageGridFluidInsertHeld extends MessageHandlerPlayerToServer<MessageGridFluidInsertHeld> implements IMessage {
    public MessageGridFluidInsertHeld() {
        //NO OP
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        //NO OP
    }

    @Override
    public void toBytes(ByteBuf buf) {
        //NO OP
    }

    @Override
    public void handle(MessageGridFluidInsertHeld message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (container instanceof ContainerGrid) {
            IGrid grid = ((ContainerGrid) container).getGrid();

            if (grid.getFluidHandler() != null) {
                grid.getFluidHandler().onInsertHeldContainer(player);
            }
        }
    }
}
