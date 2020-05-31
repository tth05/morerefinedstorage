package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RSGui;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeExternalStorage;
import com.raoulvdberge.refinedstorage.block.info.BlockDirection;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsCable;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsExternalStorage;
import com.raoulvdberge.refinedstorage.tile.TileExternalStorage;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

public class BlockExternalStorage extends BlockCable {
    public BlockExternalStorage() {
        super(createBuilder("external_storage").tileEntity(TileExternalStorage::new).create());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "direction=north,down=false,east=true,north=false,south=false,up=false,west=true"));

        registerCover(modelRegistration);
    }

    @Override
    @Nonnull
    public BlockDirection getDirection() {
        return BlockDirection.ANY;
    }

    @Override
    public List<CollisionGroup> getCollisions(TileEntity tile, IBlockState state) {
        List<CollisionGroup> groups = super.getCollisions(tile, state);

        switch (state.getValue(getDirection().getProperty())) {
            case NORTH:
                groups.add(ConstantsCable.HOLDER_NORTH);
                groups.add(ConstantsExternalStorage.HEAD_NORTH);
                break;
            case EAST:
                groups.add(ConstantsCable.HOLDER_EAST);
                groups.add(ConstantsExternalStorage.HEAD_EAST);
                break;
            case SOUTH:
                groups.add(ConstantsCable.HOLDER_SOUTH);
                groups.add(ConstantsExternalStorage.HEAD_SOUTH);
                break;
            case WEST:
                groups.add(ConstantsCable.HOLDER_WEST);
                groups.add(ConstantsExternalStorage.HEAD_WEST);
                break;
            case UP:
                groups.add(ConstantsCable.HOLDER_UP);
                groups.add(ConstantsExternalStorage.HEAD_UP);
                break;
            case DOWN:
                groups.add(ConstantsCable.HOLDER_DOWN);
                groups.add(ConstantsExternalStorage.HEAD_DOWN);
                break;
        }

        return groups;
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!canAccessGui(state, world, pos, hitX, hitY, hitZ)) {
            return false;
        }

        return openNetworkGui(RSGui.EXTERNAL_STORAGE, player, world, pos, side);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull Block block, @Nonnull BlockPos fromPos) {
        super.neighborChanged(state, world, pos, block, fromPos);

        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof TileExternalStorage) {
                NetworkNodeExternalStorage externalStorage = ((TileExternalStorage) tile).getNode();

                if (externalStorage.getNetwork() != null) {
                    externalStorage.updateStorage(externalStorage.getNetwork());
                }
            }
        }
    }
}
