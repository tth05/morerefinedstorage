package com.raoulvdberge.refinedstorage.apiimpl.storage.externalstorage;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy;
import com.raoulvdberge.refinedstorage.tile.TileInterface;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class ExternalStorageProviderItem implements IExternalStorageProvider<ItemStack> {
    @Override
    public boolean canProvide(TileEntity tile, EnumFacing direction) {
        boolean isNode = tile.hasCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, direction.getOpposite());
        INetworkNodeProxy<?> nodeProxy = tile.getCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, direction.getOpposite());

        if (!(isNode && nodeProxy.getNode() instanceof IStorageProvider)) {
            return WorldUtils.getItemHandler(tile, direction.getOpposite()) != null;
        }

        return false;
    }

    @Nonnull
    @Override
    public IStorageExternal<ItemStack> provide(IExternalStorageContext context, Supplier<TileEntity> tile, EnumFacing direction) {
        return new StorageExternalItem(context, () -> {
            World world = tile.get().getWorld();
            if(world == null)
                return null;

            BlockPos pos = tile.get().getPos();

            if(!world.isBlockLoaded(pos))
                return null;

            TileEntity currentTileEntity = world.getTileEntity(pos);

            return WorldUtils.getItemHandler(currentTileEntity, direction.getOpposite());
        }, tile.get() instanceof TileInterface);
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
