package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class MessagePortableGridItemDelta implements IMessage, IMessageHandler<MessagePortableGridItemDelta, IMessage> {

    private IPortableGrid portableGrid;
    private List<StackListResult<ItemStack>> deltas;

    private List<Pair<IGridStack, Long>> clientDeltas;

    public MessagePortableGridItemDelta(IPortableGrid portableGrid, List<StackListResult<ItemStack>> deltas) {
        this.portableGrid = portableGrid;
        this.deltas = deltas;
    }

    public MessagePortableGridItemDelta() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        this.clientDeltas = new ArrayList<>(size);

        for (int i = 0; i < size; ++i) {
            long delta = buf.readLong();
            clientDeltas.add(Pair.of(StackUtils.readItemGridStack(buf), delta));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(deltas.size());

        for (StackListResult<ItemStack> delta : deltas) {
            buf.writeLong(delta.getChange());

            //real count is 0 here because later in the postChange method it is ignored.
            // If the stack doesn't exist then the count is set to the given delta, otherwise the existing stack is
            // incremented by the given delta.
            StackUtils.writeItemGridStack(buf, delta.getStack(), 0, delta.getId(), null, false,
                    portableGrid.getItemStorageTracker().get(delta.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessagePortableGridItemDelta message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            message.clientDeltas.forEach(p -> grid.getView().postChange(p.getLeft(), p.getRight()));
        });
        return null;
    }
}
