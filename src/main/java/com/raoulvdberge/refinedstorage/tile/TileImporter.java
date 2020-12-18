package com.raoulvdberge.refinedstorage.tile;

import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeImporter;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class TileImporter extends TileNode<NetworkNodeImporter> {
    public static final TileDataParameter<Integer, TileImporter> COMPARE = FilterConfig.createCompareParameter();
    public static final TileDataParameter<Integer, TileImporter> MODE = FilterConfig.createFilterModeParameter();
    public static final TileDataParameter<Integer, TileImporter> TYPE = FilterConfig.createFilterTypeParameter();

    public TileImporter() {
        dataManager.addWatchedParameter(COMPARE);
        dataManager.addWatchedParameter(MODE);
        dataManager.addWatchedParameter(TYPE);
    }

    @Override
    @Nonnull
    public NetworkNodeImporter createNode(World world, BlockPos pos) {
        return new NetworkNodeImporter(world, pos);
    }

    @Override
    public String getNodeId() {
        return NetworkNodeImporter.ID;
    }
}
