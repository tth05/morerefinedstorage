package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.gui.grid.view.GridViewItem;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class MessageGridItemUpdate implements IMessage, IMessageHandler<MessageGridItemUpdate, IMessage> {
    private boolean canCraft;
    private List<IGridStack> stacks = new ArrayList<>();
    private INetwork network;


    public MessageGridItemUpdate() {
    }

    public MessageGridItemUpdate(INetwork network, boolean canCraft) {
        this.network = network;
        this.canCraft = canCraft;
    }


    @Override
    public void fromBytes(ByteBuf buf) {
        canCraft = buf.readBoolean();

        int size = buf.readInt();

        for (int i = 0; i < size; ++i) {
            stacks.add(StackUtils.readItemGridStack(buf));
        }

    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(canCraft);

        int size = network.getItemStorageCache().getList().getStacks().size() + network.getItemStorageCache().getCraftablesList().getStacks().size();

        buf.writeInt(size);

        for (StackListEntry<ItemStack> stack : network.getItemStorageCache().getList().getStacks()) {
            StackListEntry<ItemStack> craftingEntry = network.getItemStorageCache().getCraftablesList().getEntry(stack.getStack(), IComparer.COMPARE_NBT);

            StackUtils.writeItemGridStack(buf, stack.getStack(), stack.getId(), craftingEntry != null ? craftingEntry.getId() : null, false, network.getItemStorageTracker().get(stack.getStack()));
        }

        for (StackListEntry<ItemStack> stack : network.getItemStorageCache().getCraftablesList().getStacks()) {
            StackListEntry<ItemStack> regularEntry = network.getItemStorageCache().getList().getEntry(stack.getStack(), IComparer.COMPARE_NBT);

            StackUtils.writeItemGridStack(buf, stack.getStack(), stack.getId(), regularEntry != null ? regularEntry.getId() : null, true, network.getItemStorageTracker().get(stack.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessageGridItemUpdate message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            grid.setView(new GridViewItem(grid, GuiGrid.getDefaultSorter(), GuiGrid.getSorters()));
            grid.getView().setCanCraft(message.canCraft);
            grid.getView().setStacks(message.stacks);
            grid.getView().sort();
        });

        return null;
    }
}
