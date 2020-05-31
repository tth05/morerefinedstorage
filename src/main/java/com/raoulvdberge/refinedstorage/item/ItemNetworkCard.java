package com.raoulvdberge.refinedstorage.item;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.item.info.ItemInfo;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemNetworkCard extends ItemBase {
    private static final String NBT_RECEIVER_X = "ReceiverX";
    private static final String NBT_RECEIVER_Y = "ReceiverY";
    private static final String NBT_RECEIVER_Z = "ReceiverZ";
    private static final String NBT_DIMENSION = "Dimension";

    public ItemNetworkCard() {
        super(new ItemInfo(RS.ID, "network_card"));

        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
        modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "inventory"));
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        Block block = world.getBlockState(pos).getBlock();

        if (block == RSBlocks.NETWORK_RECEIVER) {
            NBTTagCompound tag = new NBTTagCompound();

            tag.setInteger(NBT_RECEIVER_X, pos.getX());
            tag.setInteger(NBT_RECEIVER_Y, pos.getY());
            tag.setInteger(NBT_RECEIVER_Z, pos.getZ());
            tag.setInteger(NBT_DIMENSION, world.provider.getDimension());

            player.getHeldItem(hand).setTagCompound(tag);

            return EnumActionResult.SUCCESS;
        }

        return EnumActionResult.PASS;
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        BlockPos pos = getReceiver(stack);

        if (pos != null) {
            tooltip.add(I18n.format("misc.refinedstorage:network_card.tooltip", pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public static BlockPos getReceiver(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_RECEIVER_X) && stack.getTagCompound().hasKey(NBT_RECEIVER_Y) && stack.getTagCompound().hasKey(NBT_RECEIVER_Z)) {
            return new BlockPos(stack.getTagCompound().getInteger(NBT_RECEIVER_X), stack.getTagCompound().getInteger(NBT_RECEIVER_Y), stack.getTagCompound().getInteger(NBT_RECEIVER_Z));
        }

        return null;
    }

    public static int getDimension(ItemStack stack) {
        return (stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_DIMENSION)) ? stack.getTagCompound().getInteger(NBT_DIMENSION) : 0;
    }
}
