package com.raoulvdberge.refinedstorage.integration.jei;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.item.ItemCover;
import mezz.jei.api.recipe.*;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class RecipeRegistryPluginCover implements IRecipeRegistryPlugin {
    @Nonnull
    @Override
    public <V> List<String> getRecipeCategoryUids(IFocus<V> focus) {
        if (focus.getValue() instanceof ItemStack) {
            ItemStack stack = (ItemStack) focus.getValue();

            if (focus.getMode() == IFocus.Mode.INPUT) {
                if ((!RS.INSTANCE.config.hideCovers && CoverManager.isValidCover(stack)) || API.instance().getComparer().isEqualNoQuantity(stack, ItemCover.HIDDEN_COVER_ALTERNATIVE)) {
                    return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
                }
            } else if (focus.getMode() == IFocus.Mode.OUTPUT) {
                if (stack.getItem() == RSItems.COVER) {
                    return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
                }
            }
        }

        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
        if (focus.getValue() instanceof ItemStack) {
            ItemStack stack = (ItemStack) focus.getValue();

            if (focus.getMode() == IFocus.Mode.INPUT) {
                if ((!RS.INSTANCE.config.hideCovers && CoverManager.isValidCover(stack)) || API.instance().getComparer().isEqualNoQuantity(stack, ItemCover.HIDDEN_COVER_ALTERNATIVE)) {
                    ItemStack cover = new ItemStack(RSItems.COVER);

                    ItemCover.setItem(cover, stack);

                    return Collections.singletonList((T) new RecipeWrapperCover(stack, cover));
                }
            } else if (focus.getMode() == IFocus.Mode.OUTPUT) {
                if (stack.getItem() == RSItems.COVER) {
                    return Collections.singletonList((T) new RecipeWrapperCover(ItemCover.getItem(stack), stack));
                }
            }
        }

        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        return Collections.emptyList();
    }
}
