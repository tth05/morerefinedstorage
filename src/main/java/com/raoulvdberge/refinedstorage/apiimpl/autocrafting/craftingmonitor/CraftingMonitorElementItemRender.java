package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor;

import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementAttributeHolder;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawers;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.util.RenderUtils;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class CraftingMonitorElementItemRender implements ICraftingMonitorElement,
        ICraftingMonitorElementAttributeHolder {
    private static final int COLOR_PROCESSING = 0xFFD9EDF7;
    private static final int COLOR_SCHEDULED = 0xFFE8E5CA;
    private static final int COLOR_CRAFTING = 0xFFADDBC6;

    public static final String ID = "item_render";

    private final ItemStack stack;
    private int stored;
    private int processing;
    private int scheduled;
    private int crafting;

    public CraftingMonitorElementItemRender(ItemStack stack, int stored, int processing, int scheduled, int crafting) {
        this.stack = stack;
        this.stored = stored;
        this.processing = processing;
        this.scheduled = scheduled;
        this.crafting = crafting;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(int x, int y, IElementDrawers drawers) {
        if (processing > 0) {
            drawers.getOverlayDrawer().draw(x, y, COLOR_PROCESSING);
        } else if (scheduled > 0) {
            drawers.getOverlayDrawer().draw(x, y, COLOR_SCHEDULED);
        } else if (crafting > 0) {
            drawers.getOverlayDrawer().draw(x, y, COLOR_CRAFTING);
        }

        drawers.getItemDrawer().draw(x + 4, y + 6, stack);

        float scale = drawers.getFontRenderer().getUnicodeFlag() ? 1F : 0.5F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);

        int yy = y + 7;

        if (stored > 0) {
            drawers.getStringDrawer().draw(RenderUtils.getOffsetOnScale(x + 25, scale), RenderUtils.getOffsetOnScale(yy, scale), I18n.format("gui.refinedstorage:crafting_monitor.stored", stored));

            yy += 7;
        }

        if (processing > 0) {
            drawers.getStringDrawer().draw(RenderUtils.getOffsetOnScale(x + 25, scale), RenderUtils.getOffsetOnScale(yy, scale), I18n.format("gui.refinedstorage:crafting_monitor.processing", processing));

            yy += 7;
        }

        if (scheduled > 0) {
            drawers.getStringDrawer().draw(RenderUtils.getOffsetOnScale(x + 25, scale), RenderUtils.getOffsetOnScale(yy, scale), I18n.format("gui.refinedstorage:crafting_monitor.scheduled", scheduled));

            yy += 7;
        }

        //do not draw crafting if scheduled is present
        if (crafting > 0 && scheduled < 1) {
            drawers.getStringDrawer().draw(RenderUtils.getOffsetOnScale(x + 25, scale), RenderUtils.getOffsetOnScale(yy, scale), I18n.format("gui.refinedstorage:crafting_monitor.crafting", crafting));
        }

        GlStateManager.popMatrix();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getBaseId() {
        return ID;
    }

    @Nullable
    @Override
    public String getTooltip() {
        return String.join("\n", RenderUtils.getItemTooltip(this.stack));
    }

    @Override
    public int getStored() {
        return stored;
    }

    @Override
    public int getProcessing() {
        return processing;
    }

    @Override
    public int getScheduled() {
        return scheduled;
    }

    @Override
    public int getCrafting() {
        return crafting;
    }

    @Override
    public void write(ByteBuf buf) {
        StackUtils.writeItemStack(buf, stack);
        buf.writeInt(stored);
        buf.writeInt(processing);
        buf.writeInt(scheduled);
        buf.writeInt(crafting);
    }

    @Override
    public boolean merge(ICraftingMonitorElement element) {
        if (element.getId().equals(getId()) && elementHashCode() == element.elementHashCode()) {
            this.stored += ((CraftingMonitorElementItemRender) element).stored;
            this.processing += ((CraftingMonitorElementItemRender) element).processing;
            this.scheduled += ((CraftingMonitorElementItemRender) element).scheduled;
            this.crafting += ((CraftingMonitorElementItemRender) element).crafting;

            return true;
        }

        return false;
    }

    @Override
    public int elementHashCode() {
        return API.instance().getItemStackHashCode(stack);
    }

    @Override
    public int baseElementHashCode() {
        return elementHashCode();
    }
}
