package com.raoulvdberge.refinedstorage.gui.grid.stack;

import com.raoulvdberge.refinedstorage.api.storage.tracker.StorageTrackerEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.gui.GuiBase;
import com.raoulvdberge.refinedstorage.util.RenderUtils;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;

public class GridStackItem implements IGridStack {
    private UUID id;
    @Nullable
    private UUID otherId;

    private final ItemStack stack;
    private long count;
    private String cachedName;
    private boolean craftable;
    private String[] oreIds = null;
    @Nullable
    private StorageTrackerEntry entry;
    private String modId;
    private String modName;
    private String tooltip;

    public GridStackItem(ItemStack stack, long count) {
        this.stack = stack;
        this.count = count;
    }

    public GridStackItem(UUID id, @Nullable UUID otherId, ItemStack stack, long count, boolean craftable, @Nullable StorageTrackerEntry entry) {
        this.id = id;
        this.otherId = otherId;
        this.stack = stack;
        this.count = count;
        this.craftable = craftable;
        this.entry = entry;
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

    @Nullable
    static String getModNameByModId(String modId) {
        ModContainer container = Loader.instance().getActiveModList().stream()
                .filter(m -> m.getModId().equalsIgnoreCase(modId))
                .findFirst()
                .orElse(null);

        return container == null ? null : container.getName();
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public boolean isCraftable() {
        return craftable;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        try {
            if (cachedName == null) {
                cachedName = stack.getDisplayName();
            }

            return cachedName;
        } catch (Exception t) {
            return "";
        }
    }

    @Override
    public String getModId() {
        if (modId == null) {
            modId = stack.getItem().getCreatorModId(stack);

            if (modId == null) {
                modId = "???";
            }
        }

        return modId;
    }

    @Override
    public String getModName() {
        if (modName == null) {
            modName = getModNameByModId(getModId());

            if (modName == null) {
                modName = "???";
            }
        }

        return modName;
    }

    @Override
    public String[] getOreIds() {
        if (oreIds == null) {
            if (stack.isEmpty()) {
                oreIds = new String[]{};
            } else {
                oreIds = Arrays.stream(OreDictionary.getOreIDs(stack)).mapToObj(OreDictionary::getOreName).toArray(String[]::new);
            }
        }

        return oreIds;
    }

    @Override
    public String getTooltip(boolean cached) {
        if (this.tooltip == null) {
            try {
                this.tooltip = String.join("\n", RenderUtils.getItemTooltip(stack));
            } catch(Exception e) {
                this.tooltip = "";
            }
        }

        if (cached)
            return this.tooltip;

        try {
            return String.join("\n", RenderUtils.getItemTooltip(stack));
        } catch (Exception t) {
            return "";
        }
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
        return API.instance().getQuantityFormatter().format(getQuantity());
    }

    @Override
    public void draw(GuiBase gui, int x, int y) {
        String text = null;

        if (isCraftable()) {
            text = I18n.format("gui.refinedstorage:grid.craft");
        } else if (getCount() > 1) {
            text = API.instance().getQuantityFormatter().formatWithUnits(getQuantity());
        }

        gui.drawItem(x, y, stack, true, text);
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
