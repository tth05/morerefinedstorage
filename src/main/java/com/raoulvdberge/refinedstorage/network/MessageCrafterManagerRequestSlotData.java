package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.container.ContainerCrafterManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageCrafterManagerRequestSlotData
        extends MessageHandlerPlayerToServer<MessageCrafterManagerRequestSlotData> implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
        //NO OP
    }

    @Override
    public void toBytes(ByteBuf buf) {
        //NO OP
    }

    @Override
    protected void handle(MessageCrafterManagerRequestSlotData message, EntityPlayerMP player) {
        if (!(player.openContainer instanceof ContainerCrafterManager))
            return;
        for (IContainerListener listener : ((ContainerCrafterManager) player.openContainer).getListeners()) {
            if (!(listener instanceof ContainerCrafterManager.CrafterManagerListener))
                continue;
            ContainerCrafterManager.CrafterManagerListener cmListener =
                    (ContainerCrafterManager.CrafterManagerListener) listener;

            if (cmListener.getPlayer() != player)
                continue;

            cmListener.setReceivedContainerData();
            cmListener.sendAllContents(player.openContainer, player.openContainer.getInventory());
        }
    }
}
