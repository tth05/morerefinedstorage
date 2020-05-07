package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class MessagePortableGridFluidDelta
        implements IMessage, IMessageHandler<MessagePortableGridFluidDelta, IMessage> {

    private IPortableGrid portableGrid;
    private List<StackListResult<FluidStack>> deltas = new ArrayList<>();

    private List<Pair<IGridStack, Integer>> clientDeltas = new ArrayList<>();

    public MessagePortableGridFluidDelta(IPortableGrid portableGrid, List<StackListResult<FluidStack>> deltas) {
        this.portableGrid = portableGrid;
        this.deltas = deltas;
    }

    public MessagePortableGridFluidDelta() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        for (int i = 0; i < size; ++i) {
            int delta = buf.readInt();
            clientDeltas.add(Pair.of(StackUtils.readFluidGridStack(buf), delta));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(deltas.size());

        for (StackListResult<FluidStack> delta : deltas) {
            buf.writeInt(delta.getChange());

            StackUtils.writeFluidGridStack(buf, delta.getStack(), delta.getId(), null, false,
                    portableGrid.getFluidStorageTracker().get(delta.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessagePortableGridFluidDelta message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            message.clientDeltas.forEach(p -> grid.getView().postChange(p.getLeft(), p.getRight()));

            grid.getView().sort();
        });
        return null;
    }
}
