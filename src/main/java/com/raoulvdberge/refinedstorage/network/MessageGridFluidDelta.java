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
import java.util.ArrayList;
import java.util.List;

public class MessageGridFluidDelta implements IMessage, IMessageHandler<MessageGridFluidDelta, IMessage> {
    @Nullable
    private INetwork network;

    private List<StackListResult<FluidStack>> deltas;

    private List<Pair<IGridStack, Long>> clientDeltas;

    public MessageGridFluidDelta(@Nullable INetwork network, List<StackListResult<FluidStack>> deltas) {
        this.network = network;
        this.deltas = deltas;
    }

    public MessageGridFluidDelta() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        this.clientDeltas = new ArrayList<>(size);

        for (int i = 0; i < size; ++i) {
            long delta = buf.readLong();

            this.clientDeltas.add(Pair.of(StackUtils.readFluidGridStack(buf), delta));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(deltas.size());

        for (StackListResult<FluidStack> delta : deltas) {
            buf.writeLong(delta.getChange());

            StackListEntry<FluidStack> craftingEntry = network.getFluidStorageCache().getCraftablesList()
                    .getEntry(delta.getStack(), IComparer.COMPARE_NBT);

            //real count is 0 here because later in the postChange method it is ignored.
            // If the stack doesn't exist then the count is set to the given delta, otherwise the existing stack is
            // incremented by the given delta.
            StackUtils.writeFluidGridStack(buf, delta.getStack(), 0, delta.getId(),
                    craftingEntry != null ? craftingEntry.getId() : null, false,
                    network.getFluidStorageTracker().get(delta.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessageGridFluidDelta message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            message.clientDeltas.forEach(p -> grid.getView().postChange(p.getLeft(), p.getRight()));
        });

        return null;
    }
}
