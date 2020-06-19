package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.block.info.BlockDirection;
import com.raoulvdberge.refinedstorage.block.info.IBlockInfo;
import com.raoulvdberge.refinedstorage.item.itemblock.ItemBlockBase;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.AdvancedRayTraceResult;
import com.raoulvdberge.refinedstorage.render.collision.AdvancedRayTracer;
import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import com.raoulvdberge.refinedstorage.util.CollisionUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public abstract class BlockBase extends Block {
    private static final CollisionGroup DEFAULT_COLLISION_GROUP = new CollisionGroup().addItem(new AxisAlignedBB(0, 0, 0, 1, 1, 1)).setCanAccessGui(true);
    private static final List<CollisionGroup> DEFAULT_COLLISION_GROUPS = Collections.singletonList(DEFAULT_COLLISION_GROUP);

    protected final IBlockInfo info;

    public BlockBase(IBlockInfo info) {
        super(info.getMaterial());

        this.info = info;

        setHardness(info.getHardness());
        setRegistryName(info.getId());
        setCreativeTab(RS.INSTANCE.tab);
        setSoundType(info.getSoundType());
    }

    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
    }

    @Nonnull
    @Override
    public String getTranslationKey() {
        return "block." + info.getId().toString();
    }

    protected BlockStateContainer.Builder createBlockStateBuilder() {
        BlockStateContainer.Builder builder = new BlockStateContainer.Builder(this);

        if (getDirection() != null) {
            builder.add(getDirection().getProperty());
        }

        return builder;
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockStateBuilder().build();
    }

    public Item createItem() {
        return new ItemBlockBase(this, false);
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        return 0;
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        if (getDirection() != null) {
            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof TileBase) {
                return state.withProperty(getDirection().getProperty(), ((TileBase) tile).getDirection());
            }
        }

        return state;
    }

    @Override
    public int damageDropped(@Nonnull IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public boolean rotateBlock(World world, @Nonnull BlockPos pos, @Nonnull EnumFacing axis) {
        if (!world.isRemote && getDirection() != null) {
            TileBase tile = (TileBase) world.getTileEntity(pos);

            EnumFacing newDirection = getDirection().cycle(tile.getDirection());

            tile.setDirection(newDirection);

            WorldUtils.updateBlock(world, pos);

            return true;
        }

        return false;
    }

    @Override
    public void breakBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        dropContents(world, pos);
        removeTile(world, pos, state);
    }

    void removeTile(World world, BlockPos pos, IBlockState state) {
        if (hasTileEntity(state)) {
            world.removeTileEntity(pos);
        }
    }

    void dropContents(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if(!(tile instanceof TileBase))
            return;

        IItemHandler drops = ((TileBase) tile).getDrops();
        if (drops == null)
            return;

        WorldUtils.dropInventory(world, pos, drops);
    }

    @Override
    public boolean removedByPlayer(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(@Nonnull World world, @Nonnull EntityPlayer player, @Nonnull BlockPos pos, @Nonnull IBlockState state, TileEntity tile, @Nonnull ItemStack stack) {
        super.harvestBlock(world, player, pos, state, tile, stack);

        world.setBlockToAir(pos);
    }

    @Override
    public final boolean hasTileEntity(@Nonnull IBlockState state) {
        return info.hasTileEntity();
    }

    @Override
    public final TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return info.createTileEntity();
    }

    @Nullable
    public BlockDirection getDirection() {
        return null;
    }

    public final IBlockInfo getInfo() {
        return info;
    }

    protected boolean canAccessGui(IBlockState state, World world, BlockPos pos, float hitX, float hitY, float hitZ) {
        state = getActualState(state, world, pos);

        for (CollisionGroup group : getCollisions(world.getTileEntity(pos), state)) {
            if (group.canAccessGui()) {
                for (AxisAlignedBB aabb : group.getItems()) {
                    if (CollisionUtils.isInBounds(aabb, hitX, hitY, hitZ)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public List<CollisionGroup> getCollisions(TileEntity tile, IBlockState state) {
        return DEFAULT_COLLISION_GROUPS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void addCollisionBoxToList(@Nonnull IBlockState state, World world, @Nonnull BlockPos pos, @Nonnull AxisAlignedBB entityBox, @Nonnull List<AxisAlignedBB> collidingBoxes, Entity entityIn, boolean isActualState) {
        for (CollisionGroup group : getCollisions(world.getTileEntity(pos), this.getActualState(state, world, pos))) {
            for (AxisAlignedBB aabb : group.getItems()) {
                addCollisionBoxToList(pos, entityBox, collidingBoxes, aabb);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public RayTraceResult collisionRayTrace(@Nonnull IBlockState state, World world, @Nonnull BlockPos pos, @Nonnull Vec3d start, @Nonnull Vec3d end) {
        AdvancedRayTraceResult result = AdvancedRayTracer.rayTrace(pos, start, end, getCollisions(world.getTileEntity(pos), this.getActualState(state, world, pos)));

        return result != null ? result.getHit() : null;
    }
}
