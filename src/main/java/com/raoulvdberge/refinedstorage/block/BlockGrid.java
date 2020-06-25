package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeGrid;
import com.raoulvdberge.refinedstorage.block.info.BlockDirection;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.raoulvdberge.refinedstorage.item.itemblock.ItemBlockBase;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.model.baked.BakedModelFullbright;
import com.raoulvdberge.refinedstorage.tile.grid.TileGrid;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockGrid extends BlockNode {
    public static final PropertyEnum<GridType> TYPE = PropertyEnum.create("type", GridType.class);

    public BlockGrid() {
        super(BlockInfoBuilder.forId("grid").tileEntity(TileGrid::new).create());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, GridType.NORMAL.getId(), new ModelResourceLocation(info.getId(), "connected=false,direction=north,type=normal"));
        modelRegistration.setModel(this, GridType.CRAFTING.getId(), new ModelResourceLocation(info.getId(), "connected=false,direction=north,type=crafting"));
        modelRegistration.setModel(this, GridType.PATTERN.getId(), new ModelResourceLocation(info.getId(), "connected=false,direction=north,type=pattern"));
        modelRegistration.setModel(this, GridType.FLUID.getId(), new ModelResourceLocation(info.getId(), "connected=false,direction=north,type=fluid"));

        modelRegistration.addBakedModelOverride(info.getId(), base -> new BakedModelFullbright(
            base,
            RS.ID + ":blocks/grid/cutouts/front_connected",
            RS.ID + ":blocks/grid/cutouts/crafting_front_connected",
            RS.ID + ":blocks/grid/cutouts/pattern_front_connected",
            RS.ID + ":blocks/grid/cutouts/fluid_front_connected"
        ));
    }

    @Nonnull
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    @Nullable
    public BlockDirection getDirection() {
        return BlockDirection.HORIZONTAL;
    }

    @Override
    public void getSubBlocks(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        for (int i = 0; i <= 3; i++) {
            items.add(new ItemStack(this, 1, i));
        }
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockStateBuilder()
            .add(TYPE)
            .build();
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TYPE, meta == 0 ? GridType.NORMAL : (meta == 1 ? GridType.CRAFTING : (meta == 2 ? GridType.PATTERN : GridType.FLUID)));
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        return state.getValue(TYPE) == GridType.NORMAL ? 0 : (state.getValue(TYPE) == GridType.CRAFTING ? 1 : (state.getValue(TYPE) == GridType.PATTERN ? 2 : 3));
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        return openNetworkGui(player, world, pos, side, () -> API.instance().getGridManager().openGrid(NetworkNodeGrid.FACTORY_ID, (EntityPlayerMP) player, pos));
    }

    @Override
    public boolean hasConnectedState() {
        return true;
    }

    @Override
    public Item createItem() {
        return new ItemBlockBase(this, true);
    }
}
