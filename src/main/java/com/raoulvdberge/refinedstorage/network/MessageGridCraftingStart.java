package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageGridCraftingStart extends MessageHandlerPlayerToServer<MessageGridCraftingStart> implements IMessage {
    private UUID id;
    private int quantity;
    private boolean fluids;

    public MessageGridCraftingStart() {
    }

    public MessageGridCraftingStart(UUID id, int quantity, boolean fluids) {
        this.id = id;
        this.quantity = quantity;
        this.fluids = fluids;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
        quantity = buf.readInt();
        fluids = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeLong(id.getMostSignificantBits());
        buf.writeInt(quantity);
        buf.writeBoolean(fluids);
    }

    @Override
    public void handle(MessageGridCraftingStart message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (container instanceof ContainerGrid) {
            IGrid grid = ((ContainerGrid) container).getGrid();

            if (message.fluids) {
                if (grid.getFluidHandler() != null) {
                    grid.getFluidHandler().onCraftingRequested(player, message.id, message.quantity);
                }
            } else {
                if (grid.getItemHandler() != null) {
                    grid.getItemHandler().onCraftingRequested(player, message.id, message.quantity);
                }
            }
        }
    }
}
