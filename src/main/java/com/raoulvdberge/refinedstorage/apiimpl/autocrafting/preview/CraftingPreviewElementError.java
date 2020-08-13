package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview;

import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawers;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;

public class CraftingPreviewElementError implements ICraftingPreviewElement<ItemStack> {
    public static final String ID = "error";

    @Override
    public ItemStack getElement() {
        return null;
    }

    @Override
    public void draw(int x, int y, IElementDrawers drawers) {
        // NO OP
    }

    @Override
    public long getAvailable() {
        return 0;
    }

    @Override
    public long getToCraft() {
        return 0;
    }

    @Override
    public boolean hasMissing() {
        return false;
    }

    @Override
    public void writeToByteBuf(ByteBuf buf) {
        //NO OP
    }

    @Override
    public String getId() {
        return ID;
    }

    public static ICraftingPreviewElement<?> fromByteBuf(ByteBuf byteBuf) {
        return new CraftingPreviewElementError();
    }
}
