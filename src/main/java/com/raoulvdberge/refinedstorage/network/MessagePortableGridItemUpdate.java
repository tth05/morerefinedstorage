package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.gui.grid.view.GridViewItem;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class MessagePortableGridItemUpdate implements IMessage,
        IMessageHandler<MessagePortableGridItemUpdate, IMessage> {

    private IPortableGrid portableGrid;

    private final List<IGridStack> stacks = new ArrayList<>();

    public MessagePortableGridItemUpdate() {
    }

    public MessagePortableGridItemUpdate(IPortableGrid portableGrid) {
        this.portableGrid = portableGrid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        for (int i = 0; i < size; ++i) {
            stacks.add(StackUtils.readItemGridStack(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int size = portableGrid.getItemCache().getList().getStacks().size();

        buf.writeInt(size);

        for (StackListEntry<ItemStack> stack : portableGrid.getItemCache().getList().getStacks()) {
            StackUtils.writeItemGridStack(buf, stack.getStack(), stack.getId(), null, false,
                    portableGrid.getItemStorageTracker().get(stack.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessagePortableGridItemUpdate message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            grid.setView(new GridViewItem(grid, GuiGrid.getDefaultSorter(), GuiGrid.getSorters()));
            grid.getView().setStacks(message.stacks);
            grid.getView().sort();
        });
        return null;
    }
}
