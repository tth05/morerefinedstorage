package com.raoulvdberge.refinedstorage.gui.control;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.integration.jei.IntegrationJEI;
import net.minecraft.util.text.TextFormatting;

public class SideButtonGridSearchBoxMode extends SideButtonSearchBoxMode {
    public SideButtonGridSearchBoxMode(GuiGrid gui) {
        super(gui);
    }

    @Override
    protected int getSearchBoxMode() {
        return ((GuiGrid) gui).getGrid().getSearchBoxMode();
    }

    @Override
    protected void setSearchBoxMode(int mode) {
        ((GuiGrid) gui).getGrid().onSearchBoxModeChanged(mode);
        ((GuiGrid) gui).getSearchField().setMode(mode);
    }
}
