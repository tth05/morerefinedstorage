package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.ByteBufUtils;
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
        id = UUID.fromString(ByteBufUtils.readUTF8String(buf));
        shift = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, id.toString());
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
