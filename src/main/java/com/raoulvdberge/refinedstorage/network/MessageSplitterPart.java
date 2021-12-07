package com.raoulvdberge.refinedstorage.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageSplitterPart implements IMessage {

    private short id;
    private byte messageClassId = -1;
    private byte[] payload;

    public MessageSplitterPart() {
    }

    public MessageSplitterPart(short id, byte[] payload) {
        if (payload.length > 32760)
            throw new IllegalArgumentException("Invalid payload");

        this.id = id;
        this.payload = payload;
    }

    public MessageSplitterPart(short id, byte messageClassId, byte[] payload) {
        this(id, payload);
        this.messageClassId = messageClassId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.id = buf.readShort();
        this.messageClassId = buf.readByte();
        this.payload = new byte[buf.readShort()];
        buf.readBytes(this.payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(this.id);
        buf.writeByte(this.messageClassId);
        buf.writeShort(this.payload.length);
        buf.writeBytes(payload);
    }

    public short getId() {
        return id;
    }

    public byte getMessageClassId() {
        if (!isLast())
            throw new UnsupportedOperationException("Not the last part");
        return messageClassId;
    }

    public boolean isLast() {
        return this.messageClassId != -1;
    }

    public byte[] getPayload() {
        return payload;
    }
}
