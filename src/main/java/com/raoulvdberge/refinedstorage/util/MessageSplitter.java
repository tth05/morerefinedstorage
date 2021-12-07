package com.raoulvdberge.refinedstorage.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.network.MessageSplitterPart;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageSplitter {

    private static final short PART_SIZE = 32700;
    private static short SPLIT_MESSAGE_ID = 0;

    private static final Short2ObjectMap<ByteBuf> pendingBufferMap = new Short2ObjectOpenHashMap<>();

    private static final Byte2ObjectMap<IMessageHandler<IMessage, IMessage>> handlerMap = new Byte2ObjectOpenHashMap<>();
    private static final BiMap<Byte, Class<? extends IMessage>> idToClassMap = HashBiMap.create();

    public static <T extends IMessage> void register(IMessageHandler<T, IMessage> handler, Class<T> clazz, int discriminator) {
        //Forge uses bytes here as well
        idToClassMap.put((byte) discriminator, clazz);
        handlerMap.put((byte) discriminator, (IMessageHandler<IMessage, IMessage>) handler);
    }

    public static void sendToServer(IMessage message) {
        short id = SPLIT_MESSAGE_ID++;
        if (SPLIT_MESSAGE_ID == Short.MAX_VALUE)
            SPLIT_MESSAGE_ID = 0;

        byte messageId = idToClassMap.inverse().get(message.getClass());

        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        while (buffer.readableBytes() > 0) {
            byte[] target = new byte[Math.min(buffer.readableBytes(), PART_SIZE)];
            buffer.readBytes(target);

            MessageSplitterPart part;
            if (buffer.readableBytes() < 1) { //Last message
                part = new MessageSplitterPart(id, messageId, target);
            } else {
                part = new MessageSplitterPart(id, target);
            }

            RS.INSTANCE.network.sendToServer(part);
        }
    }

    public static MessageSplitterPart handle(MessageSplitterPart message, MessageContext context) {
        ByteBuf buf = pendingBufferMap.computeIfAbsent(message.getId(), (id) -> Unpooled.buffer());
        buf.writeBytes(message.getPayload());
        if (message.isLast()) {
            Class<? extends IMessage> clazz = idToClassMap.get(message.getMessageClassId());
            try {
                IMessage instance = clazz.getConstructor().newInstance();
                instance.fromBytes(buf);
                handlerMap.get(message.getMessageClassId()).onMessage(instance, context);
                pendingBufferMap.remove(message.getId());
            } catch (Throwable e) {
                System.err.println("Error while trying to create instance of message class " + message.getMessageClassId());
                e.printStackTrace();
            }
        }

        return null;
    }
}
