package com.raoulvdberge.refinedstorage.block;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeManager;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.block.info.IBlockInfo;
import com.raoulvdberge.refinedstorage.tile.TileNode;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public abstract class BlockNode extends BlockNodeProxy {
    public static final PropertyBool CONNECTED = PropertyBool.create("connected");

    public BlockNode(IBlockInfo info) {
        super(info);
    }

    @Override
    public void onBlockPlacedBy(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityLivingBase placer, @Nonnull ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);

        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof TileNode && placer instanceof EntityPlayer) {
                ((TileNode) tile).getNode().setOwner(((EntityPlayer) placer).getGameProfile().getId());
            }

            API.instance().discoverNode(world, pos);
        }
    }

    @Override
    public void breakBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        INetworkNodeManager manager = API.instance().getNetworkNodeManager(world);

        INetworkNode node = manager.getNode(pos);

        dropContents(world, pos);

        removeTile(world, pos, state);

        manager.removeNode(pos);
        manager.markForSaving();

        if (node != null && node.getNetwork() != null) {
            node.getNetwork().getNodeGraph().invalidate(Action.PERFORM, node.getNetwork().world(), node.getNetwork().getPosition());
        }
    }

    @Override
    protected BlockStateContainer.Builder createBlockStateBuilder() {
        BlockStateContainer.Builder builder = super.createBlockStateBuilder();

        if (hasConnectedState()) {
            builder.add(CONNECTED);
        }

        return builder;
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockStateBuilder().build();
    }

    @Nonnull
    @Override
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        state = super.getActualState(state, world, pos);

        if (hasConnectedState()) {
            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof TileNode) {
                return state.withProperty(CONNECTED, ((TileNode) tile).getNode().isActive());
            }
        }

        return state;
    }

    public boolean hasConnectedState() {
        return false;
    }
}
