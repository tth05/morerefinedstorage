package com.raoulvdberge.refinedstorage.gui.control;

import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.GuiFilter;
import com.raoulvdberge.refinedstorage.tile.config.RSTileConfiguration;
import net.minecraft.util.text.TextFormatting;

public class SideButtonFilterType extends SideButton {
    private final GuiFilter gui;

    public SideButtonFilterType(GuiFilter gui) {
        super(gui);

        this.gui = gui;
    }

    @Override
    public String getTooltip() {
        return GuiBase.t("sidebutton.refinedstorage:type") + "\n" + TextFormatting.GRAY + GuiBase.t("sidebutton.refinedstorage:type." + gui.getType());
    }

    @Override
    protected void drawButtonIcon(int x, int y) {
        gui.drawTexture(x, y, 16 * gui.getType().ordinal(), 128, 16, 16);
    }

    @Override
    public void actionPerformed() {
        gui.setType(gui.getType() == RSTileConfiguration.FilterType.ITEMS ? RSTileConfiguration.FilterType.FLUIDS : RSTileConfiguration.FilterType.ITEMS);
        gui.sendUpdate();
    }
}
