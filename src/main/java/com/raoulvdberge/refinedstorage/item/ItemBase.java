package com.raoulvdberge.refinedstorage.item;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.item.info.IItemInfo;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public abstract class ItemBase extends Item {
    protected final IItemInfo info;

    public ItemBase(IItemInfo info) {
        this.info = info;

        setRegistryName(info.getId());
        setCreativeTab(RS.INSTANCE.tab);
    }

    @SideOnly(Side.CLIENT)
    public void registerModels(IModelRegistration modelRegistration) {
    }

    @Nonnull
    @Override
    public String getTranslationKey() {
        return "item." + info.getId().toString();
    }

    @Nonnull
    @Override
    public String getTranslationKey(@Nonnull ItemStack stack) {
        if (getHasSubtypes()) {
            return getTranslationKey() + "." + stack.getItemDamage();
        }

        return getTranslationKey();
    }
}
