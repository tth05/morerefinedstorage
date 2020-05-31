package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.gui.grid.view.GridViewFluid;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class MessagePortableGridFluidUpdate
        implements IMessage, IMessageHandler<MessagePortableGridFluidUpdate, IMessage> {

    private IPortableGrid portableGrid;

    private final List<IGridStack> stacks = new ArrayList<>();

    public MessagePortableGridFluidUpdate(IPortableGrid portableGrid) {
        this.portableGrid = portableGrid;
    }

    public MessagePortableGridFluidUpdate() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        for (int i = 0; i < size; ++i) {
            stacks.add(StackUtils.readFluidGridStack(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int size = portableGrid.getFluidCache().getList().getStacks().size();

        buf.writeInt(size);

        for (StackListEntry<FluidStack> stack : portableGrid.getFluidCache().getList().getStacks()) {
            StackUtils.writeFluidGridStack(buf, stack.getStack(), stack.getId(), null, false,
                    portableGrid.getFluidStorageTracker().get(stack.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessagePortableGridFluidUpdate message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            grid.setView(new GridViewFluid(grid, GuiGrid.getDefaultSorter(), GuiGrid.getSorters()));
            grid.getView().setStacks(message.stacks);
            grid.getView().sort();
        });
        return null;
    }
}
