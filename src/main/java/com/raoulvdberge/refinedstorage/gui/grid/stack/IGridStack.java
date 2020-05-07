package com.raoulvdberge.refinedstorage.gui.grid.stack;

import com.raoulvdberge.refinedstorage.api.storage.tracker.StorageTrackerEntry;
import com.raoulvdberge.refinedstorage.gui.GuiBase;

import javax.annotation.Nullable;
import java.util.UUID;

public interface IGridStack {

    UUID getId();

    @Nullable
    UUID getOtherId();

    void updateOtherId(@Nullable UUID otherId);

    String getName();

    String getModId();

    String getModName();

    String[] getOreIds();

    String getTooltip();

    int getQuantity();

    String getFormattedFullQuantity();

    void draw(GuiBase gui, int x, int y);

    Object getIngredient();

    @Nullable
    StorageTrackerEntry getTrackerEntry();

    void setTrackerEntry(@Nullable StorageTrackerEntry entry);

    boolean isCraftable();
}
