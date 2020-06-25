package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.ICoverable;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.Cover;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverType;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.raoulvdberge.refinedstorage.block.info.IBlockInfo;
import com.raoulvdberge.refinedstorage.block.property.PropertyObject;
import com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsCable;
import com.raoulvdberge.refinedstorage.render.model.baked.BakedModelCableCover;
import com.raoulvdberge.refinedstorage.render.model.baked.BakedModelFullbright;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import com.raoulvdberge.refinedstorage.tile.TileCable;
import com.raoulvdberge.refinedstorage.tile.TileNode;
import com.raoulvdberge.refinedstorage.util.CollisionUtils;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class BlockCable extends BlockNode {
    public static final PropertyObject<Cover> COVER_NORTH = new PropertyObject<>("cover_north", Cover.class);
    public static final PropertyObject<Cover> COVER_EAST = new PropertyObject<>("cover_east", Cover.class);
    public static final PropertyObject<Cover> COVER_SOUTH = new PropertyObject<>("cover_south", Cover.class);
    public static final PropertyObject<Cover> COVER_WEST = new PropertyObject<>("cover_west", Cover.class);
    public static final PropertyObject<Cover> COVER_UP = new PropertyObject<>("cover_up", Cover.class);
    public static final PropertyObject<Cover> COVER_DOWN = new PropertyObject<>("cover_down", Cover.class);

    private static final PropertyBool NORTH = PropertyBool.create("north");
    private static final PropertyBool EAST = PropertyBool.create("east");
    private static final PropertyBool SOUTH = PropertyBool.create("south");
    private static final PropertyBool WEST = PropertyBool.create("west");
    private static final PropertyBool UP = PropertyBool.create("up");
    private static final PropertyBool DOWN = PropertyBool.create("down");

    public BlockCable(IBlockInfo info) {
        super(info);
    }

    public BlockCable() {
        super(createBuilder("cable").tileEntity(TileCable::new).create());
    }

    static BlockInfoBuilder createBuilder(String id) {
        return BlockInfoBuilder.forId(id).material(Material.GLASS).soundType(SoundType.GLASS).hardness(0.35F);
    }

    @SideOnly(Side.CLIENT)
    void registerCover(IModelRegistration modelRegistration) {
        modelRegistration.addBakedModelOverride(info.getId(), BakedModelCableCover::new);
    }

    @SideOnly(Side.CLIENT)
    void registerCoverAndFullbright(IModelRegistration modelRegistration, String... textures) {
        modelRegistration.addBakedModelOverride(info.getId(), base -> new BakedModelCableCover(new BakedModelFullbright(base, textures)));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "down=false,east=true,north=false,south=false,up=false,west=true"));

        registerCover(modelRegistration);
    }

    @Override
    public boolean hasConnectedState() {
        return false;
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return super.createBlockStateBuilder()
            .add(NORTH)
            .add(EAST)
            .add(SOUTH)
            .add(WEST)
            .add(UP)
            .add(DOWN)
            .add(COVER_NORTH)
            .add(COVER_EAST)
            .add(COVER_SOUTH)
            .add(COVER_WEST)
            .add(COVER_UP)
            .add(COVER_DOWN)
            .build();
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);

        state = super.getActualState(state, world, pos)
            .withProperty(NORTH, hasConnectionWith(world, pos, this, tile, EnumFacing.NORTH))
            .withProperty(EAST, hasConnectionWith(world, pos, this, tile, EnumFacing.EAST))
            .withProperty(SOUTH, hasConnectionWith(world, pos, this, tile, EnumFacing.SOUTH))
            .withProperty(WEST, hasConnectionWith(world, pos, this, tile, EnumFacing.WEST))
            .withProperty(UP, hasConnectionWith(world, pos, this, tile, EnumFacing.UP))
            .withProperty(DOWN, hasConnectionWith(world, pos, this, tile, EnumFacing.DOWN));

        return state;
    }

    @Nonnull
    @Override
    public IBlockState getExtendedState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        IBlockState s = super.getExtendedState(state, world, pos);

        TileEntity tile = world.getTileEntity(pos);

        if (tile instanceof TileNode && ((TileNode<?>) tile).getNode() instanceof ICoverable) {
            ICoverable cover = ((ICoverable) ((TileNode<?>) tile).getNode());
            s = ((IExtendedBlockState) s).withProperty(COVER_NORTH, cover.getCoverManager().getCover(EnumFacing.NORTH));
            s = ((IExtendedBlockState) s).withProperty(COVER_EAST, cover.getCoverManager().getCover(EnumFacing.EAST));
            s = ((IExtendedBlockState) s).withProperty(COVER_SOUTH, cover.getCoverManager().getCover(EnumFacing.SOUTH));
            s = ((IExtendedBlockState) s).withProperty(COVER_WEST, cover.getCoverManager().getCover(EnumFacing.WEST));
            s = ((IExtendedBlockState) s).withProperty(COVER_UP, cover.getCoverManager().getCover(EnumFacing.UP));
            s = ((IExtendedBlockState) s).withProperty(COVER_DOWN, cover.getCoverManager().getCover(EnumFacing.DOWN));
        }

        return s;
    }

    private static boolean hasConnectionWith(IBlockAccess world, BlockPos pos, BlockBase block, TileEntity tile, EnumFacing direction) {
        if (!(tile instanceof TileNode)) {
            return false;
        }

        INetworkNode node = ((TileNode<?>) tile).getNode();

        if (node instanceof ICoverable) {
            Cover cover = ((ICoverable) node).getCoverManager().getCover(direction);

            if (cover != null && cover.getType() != CoverType.HOLLOW) {
                return false;
            }
        }

        TileEntity otherTile = world.getTileEntity(pos.offset(direction));

        if (otherTile instanceof TileNode && ((TileNode) otherTile).getNode() instanceof ICoverable) {
            Cover cover = ((ICoverable) ((TileNode) otherTile).getNode()).getCoverManager().getCover(direction.getOpposite());

            if (cover != null && cover.getType() != CoverType.HOLLOW) {
                return false;
            }
        }

        if (otherTile != null && otherTile.hasCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY, direction.getOpposite())) {
            // Prevent the block adding connections in itself
            // For example: importer cable connection on the importer face
            return block.getDirection() == null || ((TileBase) tile).getDirection() != direction;
        }

        return false;
    }

    @Override
    public List<CollisionGroup> getCollisions(TileEntity tile, IBlockState state) {
        List<CollisionGroup> groups = getCoverCollisions(tile);

        groups.add(ConstantsCable.CORE);

        if (state.getValue(NORTH)) {
            groups.add(ConstantsCable.NORTH);
        }

        if (state.getValue(EAST)) {
            groups.add(ConstantsCable.EAST);
        }

        if (state.getValue(SOUTH)) {
            groups.add(ConstantsCable.SOUTH);
        }

        if (state.getValue(WEST)) {
            groups.add(ConstantsCable.WEST);
        }

        if (state.getValue(UP)) {
            groups.add(ConstantsCable.UP);
        }

        if (state.getValue(DOWN)) {
            groups.add(ConstantsCable.DOWN);
        }

        return groups;
    }

    private List<CollisionGroup> getCoverCollisions(TileEntity tile) {
        List<CollisionGroup> groups = new ArrayList<>();

        if (tile instanceof TileNode && ((TileNode) tile).getNode() instanceof ICoverable) {
            CoverManager coverManager = ((ICoverable) ((TileNode) tile).getNode()).getCoverManager();

            Cover coverNorth = coverManager.getCover(EnumFacing.NORTH);
            Cover coverEast = coverManager.getCover(EnumFacing.EAST);
            Cover coverSouth = coverManager.getCover(EnumFacing.SOUTH);
            Cover coverWest = coverManager.getCover(EnumFacing.WEST);
            Cover coverUp = coverManager.getCover(EnumFacing.UP);
            Cover coverDown = coverManager.getCover(EnumFacing.DOWN);

            if (coverNorth != null) {
                groups.add(new CollisionGroup().addItem(CollisionUtils.getBounds(
                    coverWest != null ? 2 : 0, coverDown != null ? 2 : 0, 0,
                    coverEast != null ? 14 : 16, coverUp != null ? 14 : 16, 2
                )).setDirection(EnumFacing.NORTH));

                if (coverNorth.getType() != CoverType.HOLLOW) {
                    groups.add(ConstantsCable.HOLDER_NORTH);
                }
            }

            if (coverEast != null) {
                groups.add(new CollisionGroup().addItem(CollisionUtils.getBounds(
                    14, coverDown != null ? 2 : 0, 0,
                    16, coverUp != null ? 14 : 16, 16
                )).setDirection(EnumFacing.EAST));

                if (coverEast.getType() != CoverType.HOLLOW) {
                    groups.add(ConstantsCable.HOLDER_EAST);
                }
            }

            if (coverSouth != null) {
                groups.add(new CollisionGroup().addItem(CollisionUtils.getBounds(
                    coverEast != null ? 14 : 16, coverDown != null ? 2 : 0, 16,
                    coverWest != null ? 2 : 0, coverUp != null ? 14 : 16, 14
                )).setDirection(EnumFacing.SOUTH));

                if (coverSouth.getType() != CoverType.HOLLOW) {
                    groups.add(ConstantsCable.HOLDER_SOUTH);
                }
            }

            if (coverWest != null) {
                groups.add(new CollisionGroup().addItem(CollisionUtils.getBounds(
                    0, coverDown != null ? 2 : 0, 0,
                    2, coverUp != null ? 14 : 16, 16
                )).setDirection(EnumFacing.WEST));

                if (coverWest.getType() != CoverType.HOLLOW) {
                    groups.add(ConstantsCable.HOLDER_WEST);
                }
            }

            if (coverUp != null) {
                groups.add(new CollisionGroup().addItem(CollisionUtils.getBounds(
                    0, 14, 0,
                    16, 16, 16
                )).setDirection(EnumFacing.UP));

                if (coverUp.getType() != CoverType.HOLLOW) {
                    groups.add(ConstantsCable.HOLDER_UP);
                }
            }

            if (coverDown != null) {
                groups.add(new CollisionGroup().addItem(CollisionUtils.getBounds(
                    0, 0, 0,
                    16, 2, 16
                )).setDirection(EnumFacing.DOWN));

                if (coverDown.getType() != CoverType.HOLLOW) {
                    groups.add(ConstantsCable.HOLDER_DOWN);
                }
            }
        }

        return groups;
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
    @SuppressWarnings("deprecation")
    public IBlockState getStateForPlacement(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ, int meta, @Nonnull EntityLivingBase entity) {
        IBlockState state = super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, entity);

        if (getDirection() != null) {
            return state.withProperty(getDirection().getProperty(), getDirection().getFrom(facing, pos, entity));
        }

        return state;
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
