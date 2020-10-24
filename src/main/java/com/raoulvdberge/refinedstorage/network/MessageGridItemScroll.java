package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheItem;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageGridItemScroll extends MessageHandlerPlayerToServer<MessageGridItemScroll> implements IMessage {

    private UUID id;
    private boolean shift;
    private boolean up;

    public MessageGridItemScroll() {
    }

    public MessageGridItemScroll(UUID id, boolean shift, boolean up) {
        this.id = id;
        this.shift = shift;
        this.up = up;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
        shift = buf.readBoolean();
        up = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeBoolean(shift);
        buf.writeBoolean(up);
    }

    @Override
    protected void handle(MessageGridItemScroll message, EntityPlayerMP player) {
        if (player == null)
            return;
        Container container = player.openContainer;

        if (!(container instanceof ContainerGrid))
            return;
        IGrid grid = ((ContainerGrid) container).getGrid();

        if (grid.getItemHandler() == null)
            return;

        if (message.up && !message.shift) { //insert to grid from cursor
            grid.getItemHandler().onInsert(player, player.inventory.getItemStack(), true);
            player.updateHeldItem();
            return;
        }

        if (message.up) { //insert to grid from inventory
            StorageCacheItem cache = (StorageCacheItem) grid.getStorageCache();
            if (cache == null)
                return;

            StackListEntry<ItemStack> entry = cache.getList().get(message.id);
            if (entry == null)
                return;

            ItemStack stack = entry.getStack();
            stack.setCount((int) entry.getCount());
            int slot = player.inventory.storeItemStack(stack);
            if (slot != -1) {
                grid.getItemHandler().onInsert(player, player.inventory.getStackInSlot(slot), true);
            }

            return;
        }

        //not over grid stack -> can't extract anything
        if (message.id.equals(new UUID(0, 0)))
            return;

        if (!message.shift) { //extract from grid to cursor
            grid.getItemHandler().onExtract(player, message.id, -1, ItemGridHandler.EXTRACT_SINGLE);
            return;
        }

        //extract from grid to inventory
        grid.getItemHandler().onExtract(player, message.id, -1, ItemGridHandler.EXTRACT_SINGLE | ItemGridHandler.EXTRACT_SHIFT);
    }
}
