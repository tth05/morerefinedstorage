package com.raoulvdberge.refinedstorage.apiimpl.network.node.cover;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.ICoverable;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeCable;
import com.raoulvdberge.refinedstorage.item.ItemCover;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class CoverManager {
    private static final String NBT_DIRECTION = "Direction";
    private static final String NBT_ITEM = "Item";
    private static final String NBT_TYPE = "Type";

    private final Map<EnumFacing, Cover> covers = new EnumMap<>(EnumFacing.class);
    private final NetworkNode node;

    public CoverManager(NetworkNode node) {
        this.node = node;
    }

    public boolean canConduct(EnumFacing direction) {
        Cover cover = getCover(direction);
        if (cover != null && cover.getType() != CoverType.HOLLOW) {
            return false;
        }

        INetworkNode neighbor =
                API.instance().getNetworkNodeManager(node.getWorld()).getNode(node.getPos().offset(direction));
        if (neighbor instanceof ICoverable) {
            cover = ((ICoverable) neighbor).getCoverManager().getCover(direction.getOpposite());

            return cover == null || cover.getType() == CoverType.HOLLOW;
        }

        return true;
    }

    @Nullable
    public Cover getCover(EnumFacing facing) {
        return covers.get(facing);
    }

    public boolean hasCover(EnumFacing facing) {
        return covers.containsKey(facing);
    }

    public boolean setCover(EnumFacing facing, @Nullable Cover cover) {
        if (cover == null || (isValidCover(cover.getStack()) && !hasCover(facing))) {
            if (cover != null && facing == node.getDirection() && !(node instanceof NetworkNodeCable) &&
                    cover.getType() != CoverType.HOLLOW) {
                return false;
            }

            if (cover == null) {
                covers.remove(facing);
            } else {
                covers.put(facing, cover);
            }

            node.markDirty();

            if (node.getNetwork() != null) {
                node.getNetwork().getNodeGraph()
                        .invalidate(Action.PERFORM, node.getNetwork().world(), node.getNetwork().getPosition());
            }

            return true;
        }

        return false;
    }

    public void readFromNbt(NBTTagList list) {
        covers.clear();

        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound tag = list.getCompoundTagAt(i);

            if (tag.hasKey(NBT_DIRECTION) && tag.hasKey(NBT_ITEM)) {
                EnumFacing direction = EnumFacing.byIndex(tag.getInteger(NBT_DIRECTION));
                ItemStack item = new ItemStack(tag.getCompoundTag(NBT_ITEM));
                int type = tag.hasKey(NBT_TYPE) ? tag.getInteger(NBT_TYPE) : 0;

                if (type >= CoverType.values().length) {
                    type = 0;
                }

                if (isValidCover(item)) {
                    covers.put(direction, new Cover(item, CoverType.values()[type]));
                }
            }
        }
    }

    public NBTTagList writeToNbt() {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<EnumFacing, Cover> entry : covers.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();

            tag.setInteger(NBT_DIRECTION, entry.getKey().ordinal());
            tag.setTag(NBT_ITEM, entry.getValue().getStack().serializeNBT());
            tag.setInteger(NBT_TYPE, entry.getValue().getType().ordinal());

            list.appendTag(tag);
        }

        return list;
    }

    public IItemHandlerModifiable getAsInventory() {
        ItemStackHandler handler = new ItemStackHandler(covers.size());

        int i = 0;

        for (Map.Entry<EnumFacing, Cover> entry : covers.entrySet()) {
            ItemStack cover = entry.getValue().getType().createStack();

            ItemCover.setItem(cover, entry.getValue().getStack());

            handler.setStackInSlot(i++, cover);
        }

        return handler;
    }

    @SuppressWarnings("deprecation")
    public static boolean isValidCover(ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }

        Block block = getBlock(item);

        IBlockState state = getBlockState(item);

        return block != null && state != null &&
                ((isModelSupported(state) && block.isTopSolid(state) && !block.getTickRandomly() &&
                        !block.hasTileEntity(state)) || block instanceof BlockGlass ||
                        block instanceof BlockStainedGlass);
    }

    private static boolean isModelSupported(IBlockState state) {
        if (state.getRenderType() != EnumBlockRenderType.MODEL || state instanceof IExtendedBlockState) {
            return false;
        }

        return state.isFullCube();
    }

    @Nullable
    public static Block getBlock(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }

        Block block = Block.getBlockFromItem(item.getItem());

        if (block == Blocks.AIR) {
            return null;
        }

        return block;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static IBlockState getBlockState(@Nullable ItemStack item) {
        Block block = getBlock(item);

        if (block == null) {
            return null;
        }

        try {
            return block.getStateFromMeta(item.getItem().getMetadata(item));
        } catch (Exception e) {
            return null;
        }
    }
}
