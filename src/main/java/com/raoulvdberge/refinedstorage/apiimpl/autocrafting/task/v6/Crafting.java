package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.task.v6;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.Constants;

class Crafting extends Craft {
    private static final String NBT_RECIPE = "Recipe";
    private NonNullList<ItemStack> recipe;

    Crafting(ICraftingPattern pattern, boolean root, NonNullList<ItemStack> recipe) {
        super(pattern, root);
        this.recipe = recipe;
    }

    Crafting(INetwork network, NBTTagCompound tag) throws CraftingTaskReadException {
        super(network, tag);
        this.recipe = NonNullList.create();
        NBTTagList tookList = tag.getTagList(NBT_RECIPE, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tookList.tagCount(); ++i) {
            ItemStack stack = StackUtils.deserializeStackFromNbt(tookList.getCompoundTagAt(i));

            // Can be empty.
            recipe.add(stack);
        }
    }

    NonNullList<ItemStack> getRecipe() {
        return recipe;
    }

    NBTTagCompound writeToNbt() {
        NBTTagCompound tag = super.writeToNbt();

        NBTTagList tookList = new NBTTagList();
        for (ItemStack took : this.recipe) {
            tookList.appendTag(StackUtils.serializeStackToNbt(took));
        }

        tag.setTag(NBT_RECIPE, tookList);

        return tag;
    }
}
