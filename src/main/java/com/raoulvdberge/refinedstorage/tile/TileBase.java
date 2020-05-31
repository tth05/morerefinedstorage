package com.raoulvdberge.refinedstorage.tile;

import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import com.raoulvdberge.refinedstorage.tile.direction.DirectionHandlerTile;
import com.raoulvdberge.refinedstorage.tile.direction.IDirectionHandler;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TileBase extends TileEntity {
    protected static final String NBT_DIRECTION = "Direction";

    private EnumFacing clientDirection = EnumFacing.NORTH;
    protected IDirectionHandler directionHandler = new DirectionHandlerTile();
    protected final TileDataManager dataManager = new TileDataManager(this);

    public void setDirection(EnumFacing direction) {
        clientDirection = direction;

        directionHandler.setDirection(direction);

        world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), true);

        markDirty();
    }

    public EnumFacing getDirection() {
        return world.isRemote ? clientDirection : directionHandler.getDirection();
    }

    public TileDataManager getDataManager() {
        return dataManager;
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        directionHandler.writeToTileNbt(tag);

        return tag;
    }

    public NBTTagCompound writeUpdate(NBTTagCompound tag) {
        tag.setInteger(NBT_DIRECTION, directionHandler.getDirection().ordinal());

        return tag;
    }

    public void read(NBTTagCompound tag) {
        directionHandler.readFromTileNbt(tag);
    }

    public void readUpdate(NBTTagCompound tag) {
        boolean doRender = canCauseRenderUpdate(tag);

        clientDirection = EnumFacing.byIndex(tag.getInteger(NBT_DIRECTION));

        if (doRender) {
            WorldUtils.updateBlock(world, pos);
        }
    }

    protected boolean canCauseRenderUpdate(NBTTagCompound tag) {
        return true;
    }

    @Nonnull
    @Override
    public final NBTTagCompound getUpdateTag() {
        return writeUpdate(super.getUpdateTag());
    }

    @Nonnull
    @Override
    public final SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public final void onDataPacket(@Nonnull NetworkManager net, SPacketUpdateTileEntity packet) {
        readUpdate(packet.getNbtCompound());
    }

    @Override
    public final void handleUpdateTag(@Nonnull NBTTagCompound tag) {
        super.readFromNBT(tag);

        readUpdate(tag);
    }

    @Override
    public final void readFromNBT(@Nonnull NBTTagCompound tag) {
        super.readFromNBT(tag);

        read(tag);
    }

    @Nonnull
    @Override
    public final NBTTagCompound writeToNBT(@Nonnull NBTTagCompound tag) {
        return write(super.writeToNBT(tag));
    }

    @Override
    public boolean shouldRefresh(@Nonnull World world, @Nonnull BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Nullable
    public IItemHandler getDrops() {
        return null;
    }

    // @Volatile: Copied with some changes from the super method (avoid sending neighbor updates, it's not needed)
    @Override
    public void markDirty() {
        if (world != null) {
            world.markChunkDirty(pos, this);
        }
    }
}
