package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSGui;
import com.raoulvdberge.refinedstorage.block.enums.ControllerEnergyType;
import com.raoulvdberge.refinedstorage.block.enums.ControllerType;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.raoulvdberge.refinedstorage.item.itemblock.ItemBlockController;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.meshdefinition.ItemMeshDefinitionController;
import com.raoulvdberge.refinedstorage.render.model.baked.BakedModelFullbright;
import com.raoulvdberge.refinedstorage.tile.TileController;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class BlockController extends BlockNodeProxy {
    public static final PropertyEnum<ControllerType> TYPE = PropertyEnum.create("type", ControllerType.class);
    public static final PropertyEnum<ControllerEnergyType> ENERGY_TYPE = PropertyEnum.create("energy_type", ControllerEnergyType.class);

    public BlockController() {
        super(BlockInfoBuilder.forId("controller").tileEntity(TileController::new).create());
    }

    @Nonnull
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModelMeshDefinition(this, new ItemMeshDefinitionController());

        modelRegistration.setStateMapper(this, new StateMap.Builder().ignore(TYPE).build());

        modelRegistration.addBakedModelOverride(info.getId(), base -> new BakedModelFullbright(
            base,
            RS.ID + ":blocks/controller/cutouts/nearly_off",
            RS.ID + ":blocks/controller/cutouts/nearly_on",
            RS.ID + ":blocks/controller/cutouts/on"
        ));
    }

    @Override
    public void getSubBlocks(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        items.add(ItemBlockController.createStack(new ItemStack(this, 1, 0), 0));
        items.add(ItemBlockController.createStack(new ItemStack(this, 1, 0), RS.INSTANCE.config.controllerCapacity));
        items.add(ItemBlockController.createStack(new ItemStack(this, 1, 1), 0));
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockStateBuilder()
            .add(TYPE)
            .add(ENERGY_TYPE)
            .build();
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(TYPE, meta == 0 ? ControllerType.NORMAL : ControllerType.CREATIVE);
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        return state.getValue(TYPE) == ControllerType.NORMAL ? 0 : 1;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return super.getActualState(state, world, pos)
            .withProperty(ENERGY_TYPE, ((TileController) world.getTileEntity(pos)).getEnergyType());
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer player, @Nonnull EnumHand hand, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ) {
        return openNetworkGui(RSGui.CONTROLLER, player, world, pos, side);
    }

    @Override
    public void onBlockPlacedBy(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityLivingBase player, @Nonnull ItemStack stack) {
        if (!world.isRemote) {
            TileController controller = (TileController) world.getTileEntity(pos);

            NBTTagCompound tag = stack.getTagCompound();

            if (tag != null && tag.hasKey(TileController.NBT_ENERGY)) {
                controller.getEnergy().setStored(tag.getInteger(TileController.NBT_ENERGY));
            }
        }

        super.onBlockPlacedBy(world, pos, state, player, stack);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, int fortune) {
        ItemStack stack = new ItemStack(this, 1, getMetaFromState(state));

        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(TileController.NBT_ENERGY, ((TileController) world.getTileEntity(pos)).getEnergy().getStored());

        drops.add(stack);
    }

    @Override
    public Item createItem() {
        return new ItemBlockController(this);
    }
}
