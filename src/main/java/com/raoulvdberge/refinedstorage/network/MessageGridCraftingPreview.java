package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageGridCraftingPreview extends MessageHandlerPlayerToServer<MessageGridCraftingPreview> implements IMessage {
    private UUID id;
    private int quantity;
    private boolean noPreview;
    private boolean fluids;

    public MessageGridCraftingPreview() {
    }

    public MessageGridCraftingPreview(UUID id, int quantity, boolean noPreview, boolean fluids) {
        this.id = id;
        this.quantity = quantity;
        this.noPreview = noPreview;
        this.fluids = fluids;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = UUID.fromString(ByteBufUtils.readUTF8String(buf));
        quantity = buf.readInt();
        noPreview = buf.readBoolean();
        fluids = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, id.toString());
        buf.writeInt(quantity);
        buf.writeBoolean(noPreview);
        buf.writeBoolean(fluids);
    }

    @Override
    public void handle(MessageGridCraftingPreview message, EntityPlayerMP player) {
        Container container = player.openContainer;

        if (container instanceof ContainerGrid) {
            IGrid grid = ((ContainerGrid) container).getGrid();

            if (message.fluids) {
                if (grid.getFluidHandler() != null) {
                    grid.getFluidHandler().onCraftingPreviewRequested(player, message.id, message.quantity, message.noPreview);
                }
            } else {
                if (grid.getItemHandler() != null) {
                    grid.getItemHandler().onCraftingPreviewRequested(player, message.id, message.quantity, message.noPreview);
                }
            }
        }
    }
}
