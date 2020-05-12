package com.raoulvdberge.refinedstorage.apiimpl.network.grid.factory;

import com.raoulvdberge.refinedstorage.api.network.grid.GridFactoryType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridFactory;
import com.raoulvdberge.refinedstorage.tile.grid.WirelessCraftingGrid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class GridFactoryWirelessCraftingGrid implements IGridFactory {

    @Nullable
    public IGrid createFromStack(EntityPlayer player, ItemStack stack, int slotId) {
        return new WirelessCraftingGrid(stack, !player.getEntityWorld().isRemote, slotId);
    }

    @Nullable
    public IGrid createFromBlock(EntityPlayer player, BlockPos pos) {
        return null;
    }

    @Nullable
    public TileEntity getRelevantTile(World world, BlockPos pos) {
        return null;
    }

    public GridFactoryType getType() {
        return GridFactoryType.STACK;
    }
}
