package com.raoulvdberge.refinedstorage.item.itemblock;

import com.raoulvdberge.refinedstorage.block.BlockBase;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class ItemBlockBase extends ItemBlock {
    private final BlockBase blockBase;

    public ItemBlockBase(BlockBase blockBase, boolean subtypes) {
        super(blockBase);

        this.blockBase = blockBase;

        setRegistryName(blockBase.getInfo().getId());

        if (subtypes) {
            setMaxDamage(0);
            setHasSubtypes(true);
        }
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Nonnull
    @Override
    public String getTranslationKey(@Nonnull ItemStack stack) {
        if (getHasSubtypes()) {
            return getTranslationKey() + "." + stack.getItemDamage();
        }

        return getTranslationKey();
    }

    @Override
    public boolean placeBlockAt(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull IBlockState newState) {
        boolean result = super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState);

        if (result && blockBase.getDirection() != null) {
            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof TileBase) {
                ((TileBase) tile).setDirection(blockBase.getDirection().getFrom(side, pos, player));
            }
        }

        return result;
    }
}
