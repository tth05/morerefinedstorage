package com.raoulvdberge.refinedstorage.gui.control;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import net.minecraft.util.text.TextFormatting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SideButtonGridSize extends SideButton {
    private final Supplier<Integer> size;
    private final Consumer<Integer> handler;

    public SideButtonGridSize(GuiBase gui, Supplier<Integer> size, Consumer<Integer> handler) {
        super(gui);
        this.size = size;
        this.handler = handler;
    }

    @Override
    public String getTooltip() {
        return GuiBase.t("sidebutton.refinedstorage:grid.size") + "\n" + TextFormatting.GRAY + GuiBase.t("sidebutton.refinedstorage:grid.size." + this.size.get());
    }

    @Override
    protected void drawButtonIcon(int x, int y) {
        int size = this.size.get();

        int tx = 0;

        if (size == IGrid.SIZE_STRETCH) {
            tx = 48;
        } else if (size == IGrid.SIZE_MEDIUM) {
            tx = 16;
        } else if (size == IGrid.SIZE_LARGE) {
            tx = 32;
        }

        gui.drawTexture(x, y, 64 + tx, 64, 16, 16);
    }

    @Override
    public void actionPerformed() {
        int size = this.size.get();

        if (size == IGrid.SIZE_STRETCH) {
            size = IGrid.SIZE_SMALL;
        } else if (size == IGrid.SIZE_SMALL) {
            size = IGrid.SIZE_MEDIUM;
        } else if (size == IGrid.SIZE_MEDIUM) {
            size = IGrid.SIZE_LARGE;
        } else if (size == IGrid.SIZE_LARGE) {
            size = IGrid.SIZE_STRETCH;
        }

        this.handler.accept(size);
    }
}
