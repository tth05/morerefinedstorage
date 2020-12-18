package morerefinedstorage;

import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import net.minecraft.util.math.BlockPos;

public class DummyNetworkNode extends NetworkNode {

    public int markDirtyCallCount;

    public DummyNetworkNode() {
        //noinspection ConstantConditions
        super(new DummyWorld(), new BlockPos(0,0,0));
    }

    @Override
    public void markNetworkNodeDirty() {
        markDirtyCallCount++;
    }

    @Override
    public int getEnergyUsage() {
        return 0;
    }

    @Override
    public String getId() {
        return "null";
    }
}
