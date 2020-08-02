package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.StorageDiskSync;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.StorageDiskSyncData;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class MessageStorageDiskSizeResponse implements IMessage, IMessageHandler<MessageStorageDiskSizeResponse, IMessage> {
    private UUID id;
    private long stored;
    private long capacity;

    public MessageStorageDiskSizeResponse() {
    }

    public MessageStorageDiskSizeResponse(UUID id, long stored, long capacity) {
        this.id = id;
        this.stored = stored;
        this.capacity = capacity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
        stored = buf.readLong();
        capacity = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeLong(stored);
        buf.writeLong(capacity);
    }

    @Override
    public IMessage onMessage(MessageStorageDiskSizeResponse message, MessageContext ctx) {
        ((StorageDiskSync) API.instance().getStorageDiskSync()).setData(message.id, new StorageDiskSyncData(message.stored, message.capacity));

        return null;
    }
}
