package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.gui.grid.view.GridViewImpl;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class MessageGridFluidUpdate implements IMessage, IMessageHandler<MessageGridFluidUpdate, IMessage> {
    private INetwork network;
    private boolean canCraft;
    private final List<IGridStack> stacks = new ArrayList<>();

    public MessageGridFluidUpdate() {
    }

    public MessageGridFluidUpdate(INetwork network, boolean canCraft) {
        this.network = network;
        this.canCraft = canCraft;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        canCraft = buf.readBoolean();

        int items = buf.readInt();

        for (int i = 0; i < items; ++i) {
            this.stacks.add(StackUtils.readFluidGridStack(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(canCraft);

        int size = network.getFluidStorageCache().getList().getStacks().size() +
                network.getFluidStorageCache().getCraftablesList().getStacks().size();

        buf.writeInt(size);

        for (StackListEntry<FluidStack> stack : network.getFluidStorageCache().getList().getStacks()) {
            StackListEntry<FluidStack> craftingEntry = network.getFluidStorageCache().getCraftablesList()
                    .getEntry(stack.getStack(), IComparer.COMPARE_NBT);

            StackUtils.writeFluidGridStack(buf, stack.getStack(), stack.getCount(), stack.getId(),
                    craftingEntry != null ? craftingEntry.getId() : null, false,
                    network.getFluidStorageTracker().get(stack.getStack()));
        }

        for (StackListEntry<FluidStack> stack : network.getFluidStorageCache().getCraftablesList().getStacks()) {
            StackListEntry<FluidStack> regularEntry =
                    network.getFluidStorageCache().getList().getEntry(stack.getStack(), IComparer.COMPARE_NBT);

            StackUtils.writeFluidGridStack(buf, stack.getStack(), stack.getCount(), stack.getId(),
                    regularEntry != null ? regularEntry.getId() : null, true,
                    network.getFluidStorageTracker().get(stack.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessageGridFluidUpdate message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            grid.setView(new GridViewImpl(grid, GuiGrid.getDefaultSorter(), GuiGrid.getSorters()));
            grid.getView().setCanCraft(message.canCraft);
            grid.getView().setStacks(message.stacks);
            grid.getView().sort();
        });
        return null;
    }
}
