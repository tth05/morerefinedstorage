package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RSGui;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.storage.NetworkNodeFluidStorage;
import com.raoulvdberge.refinedstorage.block.enums.FluidStorageType;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.raoulvdberge.refinedstorage.item.itemblock.ItemBlockFluidStorage;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.tile.TileFluidStorage;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class BlockFluidStorage extends BlockNode {
    public static final PropertyEnum<FluidStorageType> TYPE = PropertyEnum.create("type", FluidStorageType.class);

    public BlockFluidStorage() {
        super(BlockInfoBuilder.forId("fluid_storage").hardness(5.8F).tileEntity(TileFluidStorage::new).create());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, FluidStorageType.TYPE_64K.getId(), new ModelResourceLocation(info.getId(), "type=64k"));
        modelRegistration.setModel(this, FluidStorageType.TYPE_256K.getId(), new ModelResourceLocation(info.getId(), "type=256k"));
        modelRegistration.setModel(this, FluidStorageType.TYPE_1024K.getId(), new ModelResourceLocation(info.getId(), "type=1024k"));
        modelRegistration.setModel(this, FluidStorageType.TYPE_4096K.getId(), new ModelResourceLocation(info.getId(), "type=4096k"));
        modelRegistration.setModel(this, FluidStorageType.TYPE_CREATIVE.getId(), new ModelResourceLocation(info.getId(), "type=creative"));
    }

    @Override
    public void getSubBlocks(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        for (int i = 0; i <= 4; ++i) {
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
        return getDefaultState().withProperty(TYPE, FluidStorageType.getById(meta));
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        return state.getValue(TYPE).getId();
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        return openNetworkGui(RSGui.FLUID_STORAGE, player, world, pos, side);
    }

    @Override
    public Item createItem() {
        return new ItemBlockFluidStorage(this);
    }

    @Override
    public void onBlockPlacedBy(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityLivingBase player, @Nonnull ItemStack stack) {
        if (!world.isRemote) {
            NetworkNodeFluidStorage storage = ((TileFluidStorage) world.getTileEntity(pos)).getNode();

            if (stack.hasTagCompound() && stack.getTagCompound().hasUniqueId(NetworkNodeFluidStorage.NBT_ID)) {
                storage.setStorageId(stack.getTagCompound().getUniqueId(NetworkNodeFluidStorage.NBT_ID));
            }

            storage.loadStorage();
        }

        // Call this after loading the storage, so the network discovery can use the loaded storage.
        super.onBlockPlacedBy(world, pos, state, player, stack);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, int fortune) {
        TileFluidStorage storage = (TileFluidStorage) world.getTileEntity(pos);

        ItemStack stack = new ItemStack(this, 1, getMetaFromState(state));

        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setUniqueId(NetworkNodeFluidStorage.NBT_ID, storage.getNode().getStorageId());

        drops.add(stack);
    }
}
