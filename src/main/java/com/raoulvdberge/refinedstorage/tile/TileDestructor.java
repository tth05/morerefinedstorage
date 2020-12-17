package com.raoulvdberge.refinedstorage.tile;

import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeDestructor;
import com.raoulvdberge.refinedstorage.tile.config.RSTileConfiguration;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class TileDestructor extends TileNode<NetworkNodeDestructor> {
    public static final TileDataParameter<Integer, TileDestructor> COMPARE = RSTileConfiguration.createCompareParameter();
    public static final TileDataParameter<Integer, TileDestructor> MODE = RSTileConfiguration.createFilterModeParameter();
    public static final TileDataParameter<Integer, TileDestructor> TYPE = RSTileConfiguration.createFilterTypeParameter();
    public static final TileDataParameter<Boolean, TileDestructor> PICKUP = new TileDataParameter<>(DataSerializers.BOOLEAN, false, t -> t.getNode().isPickupItem(), (t, v) -> {
        t.getNode().setPickupItem(v);
        t.getNode().markNetworkNodeDirty();
    });

    public TileDestructor() {
        dataManager.addWatchedParameter(COMPARE);
        dataManager.addWatchedParameter(MODE);
        dataManager.addWatchedParameter(TYPE);
        dataManager.addWatchedParameter(PICKUP);
    }

    @Override
    @Nonnull
    public NetworkNodeDestructor createNode(World world, BlockPos pos) {
        return new NetworkNodeDestructor(world, pos);
    }

    @Override
    public String getNodeId() {
        return NetworkNodeDestructor.ID;
    }
}
