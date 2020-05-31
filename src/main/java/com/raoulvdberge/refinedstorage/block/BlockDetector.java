package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSGui;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsDetector;
import com.raoulvdberge.refinedstorage.render.model.baked.BakedModelFullbright;
import com.raoulvdberge.refinedstorage.tile.TileDetector;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class BlockDetector extends BlockNode {
    private static final PropertyBool POWERED = PropertyBool.create("powered");

    public BlockDetector() {
        super(BlockInfoBuilder.forId("detector").tileEntity(TileDetector::new).create());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "inventory"));

        modelRegistration.addBakedModelOverride(info.getId(), base -> new BakedModelFullbright(base, RS.ID + ":blocks/detector/cutouts/on"));
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockStateBuilder()
            .add(POWERED)
            .build();
    }

    @Nonnull
    @Override
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return super.getActualState(state, world, pos)
            .withProperty(POWERED, ((TileDetector) world.getTileEntity(pos)).getNode().isPowered());
    }

    @Override
    public List<CollisionGroup> getCollisions(TileEntity tile, IBlockState state) {
        return Collections.singletonList(ConstantsDetector.COLLISION);
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        return openNetworkGui(RSGui.DETECTOR, player, world, pos, side);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);

        return (tile instanceof TileDetector && ((TileDetector) tile).getNode().isPowered()) ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(@Nonnull IBlockState state) {
        return false;
    }

    @Nonnull
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public BlockFaceShape getBlockFaceShape(@Nonnull IBlockAccess worldIn, @Nonnull IBlockState state, @Nonnull BlockPos pos, @Nonnull EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
}
