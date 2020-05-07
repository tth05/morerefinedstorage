package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGridNetworkAware;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageGridClear extends MessageHandlerPlayerToServer<MessageGridClear> implements IMessage {
    public MessageGridClear() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // NO OP
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // NO OP
    }

    @Override
    public void handle(MessageGridClear message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (container instanceof ContainerGrid && ((ContainerGrid) container).getGrid() instanceof IGridNetworkAware) {
            IGridNetworkAware grid = (IGridNetworkAware) ((ContainerGrid) container).getGrid();

            grid.onClear(player);
        }
    }
}
