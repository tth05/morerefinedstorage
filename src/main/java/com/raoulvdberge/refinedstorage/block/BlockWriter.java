package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.RSGui;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IReaderWriterChannel;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IWriter;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeWriter;
import com.raoulvdberge.refinedstorage.block.info.BlockDirection;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.tile.TileWriter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class BlockWriter extends BlockCable {
    public BlockWriter() {
        super(createBuilder("writer").tileEntity(TileWriter::new).create());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "connected=false,direction=north,down=false,east=true,north=false,south=false,up=false,west=true"));

        registerCoverAndFullbright(modelRegistration, RS.ID + ":blocks/writer/cutouts/connected");
    }

    @Override
    @Nullable
    public BlockDirection getDirection() {
        return BlockDirection.ANY;
    }

    @Override
    public List<CollisionGroup> getCollisions(TileEntity tile, IBlockState state) {
        return RSBlocks.CONSTRUCTOR.getCollisions(tile, state);
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!canAccessGui(state, world, pos, hitX, hitY, hitZ)) {
            return false;
        }

        if (!world.isRemote) {
            NetworkNodeWriter writer = ((TileWriter) world.getTileEntity(pos)).getNode();

            if (player.isSneaking()) {
                if (writer.getNetwork() != null) {
                    IReaderWriterChannel channel = writer.getNetwork().getReaderWriterManager().getChannel(writer.getChannel());

                    if (channel != null) {
                        channel.getHandlers().stream().map(h -> h.getStatusWriter(writer, channel)).flatMap(List::stream).forEach(player::sendMessage);
                    }
                }
            } else {
                openNetworkGui(RSGui.READER_WRITER, player, world, pos, side);
            }
        }

        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        IWriter writer = ((TileWriter) world.getTileEntity(pos)).getNode();

        return side == writer.getDirection().getOpposite() ? writer.getRedstoneStrength() : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        return getWeakPower(state, world, pos, side);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    public boolean canConnectRedstone(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);

        return tile instanceof TileWriter && side == ((TileWriter) tile).getDirection().getOpposite();
    }

    @Override
    public boolean hasConnectedState() {
        return true;
    }
}
