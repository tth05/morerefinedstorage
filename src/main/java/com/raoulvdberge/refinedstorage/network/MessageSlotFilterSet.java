package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.legacy.SlotLegacyFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageSlotFilterSet extends MessageHandlerPlayerToServer<MessageSlotFilterSet> implements IMessage {
    private int containerSlot;
    private ItemStack stack;

    public MessageSlotFilterSet(int containerSlot, ItemStack stack) {
        this.containerSlot = containerSlot;
        this.stack = stack;
    }

    public MessageSlotFilterSet() {
        // NO OP
    }

    @Override
    protected void handle(MessageSlotFilterSet message, EntityPlayerMP player) {
        if (message.stack.isEmpty() || message.stack.getCount() > message.stack.getMaxStackSize())
            return;

        Container container = player.openContainer;

        if (container == null)
            return;

        if (message.containerSlot < 0 || message.containerSlot >= container.inventorySlots.size())
            return;
        Slot slot = container.getSlot(message.containerSlot);

        if (!(slot instanceof SlotFilter) && !(slot instanceof SlotLegacyFilter))
            return;
        slot.putStack(message.stack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        containerSlot = buf.readInt();
        stack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(containerSlot);
        ByteBufUtils.writeItemStack(buf, stack);
    }
}
