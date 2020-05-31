package com.raoulvdberge.refinedstorage.tile.data;

import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.tile.ClientNode;
import com.raoulvdberge.refinedstorage.util.AccessTypeUtils;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class RSSerializers {
    public static final DataSerializer<List<ClientNode>> CLIENT_NODE_SERIALIZER = new DataSerializer<List<ClientNode>>() {
        @Override
        public void write(PacketBuffer buf, List<ClientNode> nodes) {
            buf.writeInt(nodes.size());

            for (ClientNode node : nodes) {
                ByteBufUtils.writeItemStack(buf, node.getStack());
                buf.writeInt(node.getAmount());
                buf.writeInt(node.getEnergyUsage());
            }
        }

        @Override
        public List<ClientNode> read(PacketBuffer buf) {
            List<ClientNode> nodes = new ArrayList<>();

            int size = buf.readInt();

            for (int i = 0; i < size; ++i) {
                nodes.add(new ClientNode(ByteBufUtils.readItemStack(buf), buf.readInt(), buf.readInt()));
            }

            return nodes;
        }

        @Override
        public DataParameter<List<ClientNode>> createKey(int id) {
            return null;
        }

        @Nonnull
        @Override
        public List<ClientNode> copyValue(@Nonnull List<ClientNode> value) {
            return value;
        }
    };

    public static final DataSerializer<FluidStack> FLUID_STACK_SERIALIZER = new DataSerializer<FluidStack>() {
        @Override
        public void write(@Nonnull PacketBuffer buf, @Nonnull FluidStack value) {
            if (value == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                ByteBufUtils.writeUTF8String(buf, FluidRegistry.getFluidName(value));
                buf.writeInt(value.amount);
                buf.writeCompoundTag(value.tag);
            }
        }

        @Override
        public FluidStack read(PacketBuffer buf) {
            try {
                if (buf.readBoolean()) {
                    return new FluidStack(FluidRegistry.getFluid(ByteBufUtils.readUTF8String(buf)), buf.readInt(), buf.readCompoundTag());
                }
            } catch (IOException e) {
                // NO OP
            }

            return null;
        }

        @Override
        public DataParameter<FluidStack> createKey(int id) {
            return null;
        }

        @Nonnull
        @Override
        public FluidStack copyValue(@Nonnull FluidStack value) {
            return value;
        }
    };

    public static final DataSerializer<AccessType> ACCESS_TYPE_SERIALIZER = new DataSerializer<AccessType>() {
        @Override
        public void write(PacketBuffer buf, AccessType value) {
            buf.writeInt(value.getId());
        }

        @Nonnull
        @Override
        public AccessType read(PacketBuffer buf) {
            return AccessTypeUtils.getAccessType(buf.readInt());
        }

        @Override
        public DataParameter<AccessType> createKey(int id) {
            return null;
        }

        @Nonnull
        @Override
        public AccessType copyValue(@Nonnull AccessType value) {
            return value;
        }
    };

    public static final DataSerializer<Long> LONG_SERIALIZER = new DataSerializer<Long>() {
        @Override
        public void write(PacketBuffer buf, @Nonnull Long value) {
            buf.writeLong(value);
        }

        @Override
        public Long read(PacketBuffer buf) {
            return buf.readLong();
        }

        @Override
        public DataParameter<Long> createKey(int id) {
            return null;
        }

        @Nonnull
        @Override
        public Long copyValue(@Nonnull Long value) {
            return value;
        }
    };
}
