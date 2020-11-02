package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor;

import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElement;
import com.raoulvdberge.refinedstorage.api.render.IElementDrawers;
import com.raoulvdberge.refinedstorage.util.RenderUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import javax.annotation.Nullable;

public class CraftingMonitorElementMissingPatternRender implements ICraftingMonitorElement {

    public static final String ID = "missing_pattern";

    private final ItemStack patternStack;

    public CraftingMonitorElementMissingPatternRender(ItemStack patternStack) {
        this.patternStack = patternStack;
    }

    @Override
    public void draw(int x, int y, IElementDrawers drawers) {
        drawers.getItemDrawer().draw(x + 4, y + 6, this.patternStack);

        float scale = drawers.getFontRenderer().getUnicodeFlag() ? 1F : 0.5F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);

        drawers.getStringDrawer().draw(RenderUtils.getOffsetOnScale(x + 25, scale), RenderUtils.getOffsetOnScale(y + 7, scale), I18n.format("gui.refinedstorage:crafting_monitor.missing_pattern"));

        GlStateManager.popMatrix();
    }

    @Override
    public void write(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, this.patternStack);
    }

    @Nullable
    @Override
    public String getTooltip() {
        return String.join("\n", RenderUtils.getItemTooltip(this.patternStack));
    }

    @Override
    public boolean merge(ICraftingMonitorElement element) {
        return false;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getBaseId() {
        return null;
    }

    @Override
    public int baseElementHashCode() {
        return 0;
    }

    @Override
    public int elementHashCode() {
        return 0;
    }
}
