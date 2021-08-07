package com.raoulvdberge.refinedstorage.gui.control;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.gui.GuiCrafterManager;
import com.raoulvdberge.refinedstorage.integration.jei.IntegrationJEI;
import com.raoulvdberge.refinedstorage.tile.TileCrafterManager;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import net.minecraft.util.text.TextFormatting;

public class SideButtonCrafterManagerSearchBoxMode extends SideButtonSearchBoxMode {
    public SideButtonCrafterManagerSearchBoxMode(GuiCrafterManager gui) {
        super(gui);
    }

    @Override
    protected int getSearchBoxMode() {
        return ((GuiCrafterManager) gui).getCrafterManager().getSearchBoxMode();
    }

    protected void setSearchBoxMode(int mode) {
        TileDataManager.setParameter(TileCrafterManager.SEARCH_BOX_MODE, mode);
    }
}
