package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageGridFluidPull extends MessageHandlerPlayerToServer<MessageGridFluidPull> implements IMessage {
    private UUID id;
    private boolean shift;

    public MessageGridFluidPull() {
    }

    public MessageGridFluidPull(UUID id, boolean shift) {
        this.id = id;
        this.shift = shift;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
        shift = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeBoolean(shift);
    }

    @Override
    public void handle(MessageGridFluidPull message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (container instanceof ContainerGrid) {
            IGrid grid = ((ContainerGrid) container).getGrid();

            if (grid.getFluidHandler() != null) {
                grid.getFluidHandler().onExtract(player, message.id, message.shift);
            }
        }
    }
}
