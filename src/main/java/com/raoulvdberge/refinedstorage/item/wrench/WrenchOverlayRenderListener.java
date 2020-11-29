package com.raoulvdberge.refinedstorage.item.wrench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class WrenchOverlayRenderListener {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL)
            return;

        ItemStack itemInHand = Minecraft.getMinecraft().player.getHeldItemMainhand();
        if (!(itemInHand.getItem() instanceof ItemWrench))
            return;

        ItemWrench.addDefaultMode(itemInHand);
        WrenchMode mode = WrenchMode.valueOf(itemInHand.getTagCompound().getString("mode"));

        String str = I18n.format("misc.refinedstorage:wrench.mode." + mode.name().toLowerCase());
        ScaledResolution resolution = event.getResolution();
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        fontRenderer.drawString(
                str,
                resolution.getScaledWidth() - fontRenderer.getStringWidth(str) - 5,
                resolution.getScaledHeight() - fontRenderer.FONT_HEIGHT - 5,0xFFFFFFFF, false
        );
    }
}
