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
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MessageGridItemDelta implements IMessage, IMessageHandler<MessageGridItemDelta, IMessage> {
    @Nullable
    private INetwork network;
    private List<StackListResult<ItemStack>> deltas = new ArrayList<>();

    private final List<Pair<IGridStack, Integer>> clientDeltas = new ArrayList<>();

    public MessageGridItemDelta(@Nullable INetwork network, List<StackListResult<ItemStack>> deltas) {
        this.network = network;
        this.deltas = deltas;
    }

    public MessageGridItemDelta() {
    }


    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();

        for (int i = 0; i < size; ++i) {
            int delta = buf.readInt();
            clientDeltas.add(Pair.of(StackUtils.readItemGridStack(buf), delta));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(deltas.size());

        for (StackListResult<ItemStack> delta : deltas) {
            buf.writeInt(delta.getChange());

            StackListEntry<ItemStack> craftingEntry =
                    network.getItemStorageCache().getCraftablesList().getEntry(delta.getStack(), IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);

            StackUtils.writeItemGridStack(buf, delta.getStack(), delta.getId(),
                    craftingEntry != null ? craftingEntry.getId() : null, false,
                    network.getItemStorageTracker().get(delta.getStack()));
        }
    }

    @Override
    public IMessage onMessage(MessageGridItemDelta message, MessageContext ctx) {
        GuiBase.executeLater(GuiGrid.class, grid -> {
            message.clientDeltas.forEach(p -> grid.getView().postChange(p.getLeft(), p.getRight()));

            grid.getView().sort();
        });

        return null;
    }
}
