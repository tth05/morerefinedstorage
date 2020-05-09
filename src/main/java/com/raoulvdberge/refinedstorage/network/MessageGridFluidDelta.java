package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

public class MessageGridFluidDelta implements IMessage, IMessageHandler<MessageGridFluidDelta, IMessage> {
    @Nullable
    private INetwork network;

    private List<StackListResult<FluidStack>> deltas;

    private List<Pair<IGridStack, Integer>> clientDeltas;

    public MessageGridFluidDelta(INetwork network, List<StackListResult<FluidStack>> deltas) {
        this.network = network;
        this.deltas = deltas;
    }

    public MessageGridFluidDelta() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        List<Pair<IGridStack, Integer>> clientDeltas = new LinkedList<>();

        for (int i = 0; i < size; ++i) {
            int delta = buf.readInt();

            clientDeltas.add(Pair.of(StackUtils.readFluidGridStack(buf), delta));
        }

        this.clientDeltas = clientDeltas;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(deltas.size());

        for (StackListResult<FluidStack> delta : deltas) {
            buf.writeInt(delta.getChange());

            StackListEntry<FluidStack> craftingEntry = network.getFluidStorageCache().getCraftablesList()
                    .getEntry(delta.getStack(), IComparer.COMPARE_NBT);

            StackUtils.writeFluidGridStack(buf, delta.getStack(), delta.getId(),
                    craftingEntry != null ? craftingEntry.getId() : null, false,
                    network.getFluidStorageTracker().get(delta.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessageGridFluidDelta message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            message.clientDeltas.forEach(p -> grid.getView().postChange(p.getLeft(), p.getRight()));
            grid.getView().sort();
        });

        return null;
    }
}
