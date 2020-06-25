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
    private String cachedName;
    private boolean craftable;
    private String[] oreIds = null;
    @Nullable
    private StorageTrackerEntry entry;
    private String modId;
    private String modName;
    private String tooltip;

    public GridStackItem(ItemStack stack) {
        this.stack = stack;
    }

    public GridStackItem(UUID id, @Nullable UUID otherId, ItemStack stack, boolean craftable, @Nullable StorageTrackerEntry entry) {
        this.id = id;
        this.otherId = otherId;
        this.stack = stack;
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
    public String getTooltip() {
        if (tooltip == null) {
            try {
                tooltip = String.join("\n", RenderUtils.getItemTooltip(stack));
            } catch (Exception t) {
                tooltip = "";
            }
        }

        return tooltip;
    }

    @Override
    public int getQuantity() {
        return isCraftable() ? 0 : stack.getCount();
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
        } else if (stack.getCount() > 1) {
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
