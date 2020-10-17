package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageGridPatternSlotScroll extends MessageHandlerPlayerToServer<MessageGridPatternSlotScroll>
        implements IMessage {

    private int slot;
    private boolean up;

    public MessageGridPatternSlotScroll() {
    }

    public MessageGridPatternSlotScroll(int slot, boolean up) {
        this.slot = slot;
        this.up = up;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        up = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeBoolean(up);
    }

    @Override
    protected void handle(MessageGridPatternSlotScroll message, EntityPlayerMP player) {
        if (player == null)
            return;
        Container container = player.openContainer;

        if (!(container instanceof ContainerGrid))
            return;
        IGrid grid = ((ContainerGrid) container).getGrid();

        if (grid.getItemHandler() == null)
            return;

        Slot slot = container.inventorySlots.get(message.slot);

        if (grid.getGridType() != GridType.PATTERN || !(slot instanceof SlotFilter))
            return;

        int newStackSize = MathHelper.clamp(slot.getStack().getCount() + (message.up ? 1 : -1), 1,
                slot.getStack().getMaxStackSize());

        if (newStackSize != slot.getStack().getCount()) {
            slot.getStack().setCount(newStackSize);
            slot.onSlotChanged();
        }
    }
}
