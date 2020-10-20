package com.raoulvdberge.refinedstorage.gui.grid.stack;

import com.raoulvdberge.refinedstorage.api.storage.tracker.StorageTrackerEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.UUID;

public class GridStackFluid implements IGridStack {

    private final UUID id;
    @Nullable
    private UUID otherId;

    private final FluidStack stack;
    private long count;
    @Nullable
    private StorageTrackerEntry entry;
    private final boolean craftable;
    private String modId;
    private String modName;

    public GridStackFluid(UUID id, @Nullable UUID otherId, FluidStack stack, long count, @Nullable StorageTrackerEntry entry, boolean craftable) {
        this.id = id;
        this.otherId = otherId;
        this.stack = stack;
        this.count = count;
        this.entry = entry;
        this.craftable = craftable;
    }

    @Nullable
    @Override
    public UUID getOtherId() {
        return otherId;
    }

    @Override
    public void updateOtherId(@Nullable UUID otherId) {
        this.otherId = otherId;
    }

    public FluidStack getStack() {
        return stack;
    }

    @Override
    public boolean isCraftable() {
        return craftable;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return stack.getFluid().getLocalizedName(stack);
    }

    @Override
    public String getModId() {
        if (modId == null) {
            modId = FluidRegistry.getModId(stack);

            if (modId == null) {
                modId = "???";
            }
        }

        return modId;
    }

    @Override
    public String getModName() {
        if (modName == null) {
            modName = GridStackItem.getModNameByModId(getModId());

            if (modName == null) {
                modName = "???";
            }
        }

        return modName;
    }

    @Override
    public String[] getOreIds() {
        return new String[]{stack.getFluid().getName()};
    }

    @Override
    public String getTooltip(boolean cached) {
        return stack.getFluid().getLocalizedName(stack);
    }

    @Override
    public long getQuantity() {
        return isCraftable() ? 0 : getCount();
    }

    @Override
    public long getCount() {
        return this.count;
    }

    @Override
    public void setCount(long count) {
        this.count = count;
    }

    @Override
    public void grow(long count) {
        this.count += count;
    }

    @Override
    public String getFormattedFullQuantity() {
        return API.instance().getQuantityFormatter().format(getQuantity()) + " mB";
    }

    @Override
    public void draw(GuiBase gui, int x, int y) {
        GuiBase.FLUID_RENDERER.draw(gui.mc, x, y, stack);

        String text;

        if (isCraftable()) {
            text = I18n.format("gui.refinedstorage:grid.craft");
        } else {
            text = API.instance().getQuantityFormatter().formatInBucketFormWithOnlyTrailingDigitsIfZero(getQuantity());
        }

        gui.drawQuantity(x, y, text);
    }

    @Override
    public Object getIngredient() {
        return getStack();
    }

    @Nullable
    @Override
    public StorageTrackerEntry getTrackerEntry() {
        return entry;
    }

    @Override
    public void setTrackerEntry(@Nullable StorageTrackerEntry entry) {
        this.entry = entry;
    }
}
