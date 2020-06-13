package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerCraftingMonitor;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageCraftingCancel extends MessageHandlerPlayerToServer<MessageCraftingCancel> implements IMessage {
    private UUID taskId;

    public MessageCraftingCancel() {
    }

    public MessageCraftingCancel(UUID taskId) {
        this.taskId = taskId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readBoolean()) {
            taskId = new UUID(buf.readLong(), buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(taskId != null);

        if (taskId != null) {
            buf.writeLong(taskId.getLeastSignificantBits());
            buf.writeLong(taskId.getMostSignificantBits());
        }
    }

    @Override
    public void handle(MessageCraftingCancel message, EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerCraftingMonitor) {
            ((ContainerCraftingMonitor) player.openContainer).getCraftingMonitor().onCancelled(player, message.taskId);
        } else if (player.openContainer instanceof ContainerGrid) {
            IGrid grid = ((ContainerGrid) player.openContainer).getGrid();

            if(grid.getItemHandler() != null)
                grid.getItemHandler().onCraftingCancelRequested(player, message.taskId);
        }
    }
}
