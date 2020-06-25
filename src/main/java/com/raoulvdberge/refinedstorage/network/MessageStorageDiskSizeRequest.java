package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageStorageDiskSizeRequest extends MessageHandlerPlayerToServer<MessageStorageDiskSizeRequest> implements IMessage {
    private UUID id;

    public MessageStorageDiskSizeRequest() {
    }

    public MessageStorageDiskSizeRequest(UUID id) {
        this.id = id;
    }

    @Override
    protected void handle(MessageStorageDiskSizeRequest message, EntityPlayerMP player) {
        IStorageDisk<?> disk = API.instance().getStorageDiskManager(player.getEntityWorld()).get(message.id);

        if (disk != null) {
            RS.INSTANCE.network.sendTo(new MessageStorageDiskSizeResponse(message.id, disk.getStored(), disk.getCapacity()), player);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
    }
}
