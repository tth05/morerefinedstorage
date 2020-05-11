package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageGridItemPull extends MessageHandlerPlayerToServer<MessageGridItemPull> implements IMessage {
    private UUID id;
    private int flags;

    public MessageGridItemPull() {
    }

    public MessageGridItemPull(UUID id, int flags) {
        this.id = id;
        this.flags = flags;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = UUID.fromString(ByteBufUtils.readUTF8String(buf));
        flags = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, id.toString());
        buf.writeInt(flags);
    }

    @Override
    public void handle(MessageGridItemPull message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (container instanceof ContainerGrid) {
            IGrid grid = ((ContainerGrid) container).getGrid();

            if (grid.getItemHandler() != null) {
                grid.getItemHandler().onExtract(player, message.id, message.flags);
            }
        }
    }
}
