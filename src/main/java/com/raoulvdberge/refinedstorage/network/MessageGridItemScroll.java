package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
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
    private boolean ctrl;

    public MessageGridItemScroll() {
    }

    public MessageGridItemScroll(UUID id, boolean shift, boolean ctrl, boolean up) {
        this.id = id;
        this.shift = shift;
        this.ctrl = ctrl;
        this.up = up;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
        shift = buf.readBoolean();
        ctrl = buf.readBoolean();
        up = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeBoolean(shift);
        buf.writeBoolean(ctrl);
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
        int flags = ItemGridHandler.EXTRACT_SINGLE;
        if (!message.id.equals(new UUID(0, 0))) { //isOverStack
            if (message.shift && !message.ctrl) { //shift
                flags |= ItemGridHandler.EXTRACT_SHIFT;
                if (message.up) { //scroll up
                    StorageCacheItem cache = (StorageCacheItem) grid.getStorageCache();
                    if (cache == null)
                        return;

                    ItemStack stack = cache.getList().get(message.id);
                    if (stack == null)
                        return;

                    int slot = player.inventory.storeItemStack(stack);
                    if (slot != -1) {
                        grid.getItemHandler()
                                .onInsert(player, player.inventory.getStackInSlot(slot), true);
                        return;
                    }
                } else { //scroll down
                    grid.getItemHandler().onExtract(player, message.id, -1, flags);
                    return;
                }
            } else { //ctrl
                if (!message.up) { //scroll down
                    grid.getItemHandler().onExtract(player, message.id, -1, flags);
                    return;
                }
            }
        }
        if (message.up) { //scroll up
            grid.getItemHandler().onInsert(player, player.inventory.getItemStack(), true);
            player.updateHeldItem();
        }
    }
}
