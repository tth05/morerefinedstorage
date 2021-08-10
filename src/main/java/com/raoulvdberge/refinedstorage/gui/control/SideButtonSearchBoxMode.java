package com.raoulvdberge.refinedstorage.gui.control;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.integration.jei.IntegrationJEI;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.List;

public abstract class SideButtonSearchBoxMode extends SideButton {
    private static final List<Integer> MODE_ROTATION = Arrays.asList(
            IGrid.SEARCH_BOX_MODE_NORMAL,
            IGrid.SEARCH_BOX_MODE_NORMAL_AUTOSELECTED,
            IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED,
            IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED,
            IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_2WAY,
            IGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_2WAY_AUTOSELECTED,
            IGrid.SEARCH_BOX_MODE_NORMAL
    );

    private static int nextMode(int oldMode) {
        return MODE_ROTATION.get(MODE_ROTATION.indexOf(oldMode) + 1);
    }

    public SideButtonSearchBoxMode(GuiBase gui) {
        super(gui);
    }

    @Override
    public String getTooltip() {
        return I18n.format("sidebutton.refinedstorage:grid.search_box_mode") + "\n" + TextFormatting.GRAY + I18n.format("sidebutton.refinedstorage:grid.search_box_mode." + getSearchBoxMode());
    }

    @Override
    protected void drawButtonIcon(int x, int y) {
        int mode = getSearchBoxMode();

        gui.drawTexture(x, y, IGrid.isSearchBoxModeWithAutoselection(mode) ? 16 : 0, 96, 16, 16);
    }

    @Override
    public void actionPerformed() {
        int mode = nextMode(getSearchBoxMode());

        if (IGrid.doesSearchBoxModeUseJEI(mode) && !IntegrationJEI.isLoaded()) {
            mode = IGrid.SEARCH_BOX_MODE_NORMAL;
        }

        setSearchBoxMode(mode);
    }

    protected abstract int getSearchBoxMode();

    protected abstract void setSearchBoxMode(int mode);
}
