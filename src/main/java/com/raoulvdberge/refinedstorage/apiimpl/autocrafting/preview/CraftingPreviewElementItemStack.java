package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview;

import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElement;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawers;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.util.RenderUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

public class CraftingPreviewElementItemStack implements ICraftingPreviewElement<ItemStack> {
    public static final String ID = "item_renderer";

    private ItemStack stack;
    private long available;
    private boolean missing;
    // If missing is true then toCraft is the missing amount
    private long toCraft;

    public CraftingPreviewElementItemStack(ItemStack stack) {
        this.stack = ItemHandlerHelper.copyStackWithSize(stack, 1);
    }

    public CraftingPreviewElementItemStack(ItemStack stack, long available, boolean missing, long toCraft) {
        this.stack = stack;
        this.available = available;
        this.missing = missing;
        this.toCraft = toCraft;
    }

    @Override
    public void writeToByteBuf(ByteBuf buf) {
        buf.writeInt(Item.getIdFromItem(stack.getItem()));
        buf.writeInt(stack.getMetadata());
        ByteBufUtils.writeTag(buf, stack.getTagCompound());
        buf.writeLong(available);
        buf.writeBoolean(missing);
        buf.writeLong(toCraft);
    }

    public static CraftingPreviewElementItemStack fromByteBuf(ByteBuf buf) {
        Item item = Item.getItemById(buf.readInt());
        int meta = buf.readInt();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        long available = buf.readLong();
        boolean missing = buf.readBoolean();
        long toCraft = buf.readLong();

        ItemStack stack = new ItemStack(item, 1, meta);
        stack.setTagCompound(tag);

        return new CraftingPreviewElementItemStack(stack, available, missing, toCraft);
    }

    @Override
    public ItemStack getElement() {
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(int x, int y, IElementDrawers drawers) {
        if (missing) {
            drawers.getOverlayDrawer().draw(x, y, 0xFFF2DEDE);
        }

        x += 5;
        y += 7;

        drawers.getItemDrawer().draw(x, y, getElement());

        float scale = drawers.getFontRenderer().getUnicodeFlag() ? 1F : 0.5F;

        y += 2;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);

        if (getToCraft() > 0) {
            String format = hasMissing() ? "gui.refinedstorage:crafting_preview.missing" :
                    "gui.refinedstorage:crafting_preview.to_craft";
            drawers.getStringDrawer()
                    .draw(RenderUtils.getOffsetOnScale(x + 23, scale), RenderUtils.getOffsetOnScale(y, scale),
                            GuiBase.t(format, getToCraft()));

            y += 7;
        }

        if (getAvailable() > 0) {
            drawers.getStringDrawer()
                    .draw(RenderUtils.getOffsetOnScale(x + 23, scale), RenderUtils.getOffsetOnScale(y, scale),
                            GuiBase.t("gui.refinedstorage:crafting_preview.available", getAvailable()));
        }

        GlStateManager.popMatrix();
    }

    public void addAvailable(long amount) {
        this.available += amount;
    }

    @Override
    public long getAvailable() {
        return available;
    }

    public void addToCraft(long amount) {
        this.toCraft += amount;
    }

    @Override
    public long getToCraft() {
        return this.toCraft;
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    @Override
    public boolean hasMissing() {
        return missing;
    }

    @Override
    public String getId() {
        return ID;
    }
}
