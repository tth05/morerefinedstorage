package com.raoulvdberge.refinedstorage.tile.config;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameterClientListener;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class RSTileConfiguration {
    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createFilterTypeParameter(@Nullable TileDataParameterClientListener<Integer> clientListener) {
        return new TileDataParameter<>(DataSerializers.VARINT, FilterType.ITEMS.ordinal(),
                t -> ((IRSTileConfigurationProvider) t.getNode()).getConfig().getFilterType().ordinal(),
                (t, v) -> {
                    if (v < 0 || v > FilterType.values().length)
                        return;
                    ((IRSTileConfigurationProvider) t.getNode()).getConfig().setFilterType(FilterType.values()[v]);
                }, clientListener);
    }

    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createFilterTypeParameter() {
        return createFilterTypeParameter(null);
    }

    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createCompareParameter() {
        return new TileDataParameter<>(DataSerializers.VARINT, 0,
                t -> ((IRSTileConfigurationProvider) t.getNode()).getConfig().getCompare(),
                (t, v) -> ((IRSTileConfigurationProvider) t.getNode()).getConfig().setCompare(v)
        );
    }

    public static <T extends TileEntity & INetworkNodeProxy> TileDataParameter<Integer, T> createFilterModeParameter() {
        return new TileDataParameter<>(DataSerializers.VARINT, 0,
                t -> ((IRSTileConfigurationProvider) t.getNode()).getConfig().getFilterMode().ordinal(),
                (t, v) -> {
                    if (v < 0 || v > FilterMode.values().length)
                        return;
                    ((IRSTileConfigurationProvider) t.getNode()).getConfig().setFilterMode(FilterMode.values()[v]);
                });
    }

    private final INetworkNode node;

    //filters
    private FilterType allowedFilterType;
    private FilterType filterType;
    private ItemHandlerBase itemFilters;
    private FluidInventory fluidFilters;
    private List<ItemStack> itemStacks;
    private List<FluidStack> fluidStacks;

    //compare
    private int compare;

    //filter mode
    private FilterMode allowedFilterMode;
    private FilterMode filterMode;

    private RSTileConfiguration(@Nonnull INetworkNode node, @Nonnull FilterType allowedFilterType, int itemFilterSize, int fluidFilterSize, int initialCompare, @Nonnull FilterMode allowedFilterMode, @Nonnull FilterMode initialFilterMode) {
        this.node = node;
        this.allowedFilterType = allowedFilterType;
        this.allowedFilterMode = allowedFilterMode;
        this.filterMode = initialFilterMode;
        this.compare = initialCompare;

        if (allowedFilterType == FilterType.ITEMS || allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
            this.itemFilters = new ItemHandlerBase(itemFilterSize, new ListenerNetworkNode(node)) {
                @Override
                protected void onContentsChanged(int slot) {
                    super.onContentsChanged(slot);
                    invalidateCache();
                }
            };
        }
        if (allowedFilterType == FilterType.FLUIDS || allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
            this.fluidFilters = new FluidInventory(fluidFilterSize, new ListenerNetworkNode(node)) {
                @Override
                public void setFluid(int slot, @Nullable FluidStack stack) {
                    super.setFluid(slot, stack);
                    invalidateCache();
                }
            };
        }

        invalidateCache();
    }

    public boolean acceptsItem(ItemStack stack) {
        int compareValue = this.compare == 0 ? IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT : getCompare();

        if (this.itemStacks.isEmpty())
            return isBlacklistMode();

        for (ItemStack filterStack : this.itemStacks) {
            if (API.instance().getComparer().isEqual(stack, filterStack, compareValue) == isWhitelistMode())
                return true;
        }

        return false;
    }

    public boolean acceptsFluid(FluidStack stack) {
        int compareValue = this.compare == 0 ? IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT : getCompare();

        if (this.fluidStacks.isEmpty())
            return getFilterMode() == FilterMode.BLACKLIST;

        for (FluidStack filterStack : this.fluidStacks) {
            if (API.instance().getComparer().isEqual(stack, filterStack, compareValue) == (getFilterMode() == FilterMode.WHITELIST))
                return true;
        }

        return false;
    }

    public void invalidateCache() {
        if (this.getFilterType() == FilterType.ITEMS) {
            this.itemStacks = new ArrayList<>();
            for (int i = 0; i < this.itemFilters.getSlots(); i++) {
                ItemStack stack = this.itemFilters.getStackInSlot(i);
                if (stack.isEmpty())
                    continue;

                this.itemStacks.add(stack);
            }
        } else if (this.getFilterType() == FilterType.FLUIDS) {
            this.fluidStacks = new ArrayList<>();
            for (int i = 0; i < this.fluidFilters.getSlots(); i++) {
                FluidStack stack = this.fluidFilters.getFluid(i);
                if (stack == null || stack.amount < 1)
                    continue;

                this.fluidStacks.add(stack);
            }
        }
    }

    public void setCompare(int compare) {
        if (compare < 1)
            throw new IllegalArgumentException();
        if (this.compare == 0)
            throw new UnsupportedOperationException();
        this.compare = compare;

        this.node.markNetworkNodeDirty();
    }

    public void setFilterMode(@Nonnull FilterMode filterMode) {
        if (this.allowedFilterMode != FilterMode.WHITELIST_AND_BLACKLIST)
            throw new UnsupportedOperationException();
        this.filterMode = filterMode;

        this.node.markNetworkNodeDirty();
    }

    public void setFilterType(@Nonnull FilterType filterType) {
        if (this.allowedFilterType != FilterType.ITEMS_AND_FLUIDS)
            throw new UnsupportedOperationException();
        this.filterType = filterType;

        this.node.markNetworkNodeDirty();
    }

    public boolean isItemFilterEmpty() {
        return this.itemStacks.isEmpty();
    }

    public boolean isFluidFilterEmpty() {
        return this.fluidStacks.isEmpty();
    }

    public boolean isFilterTypeItem() {
        return this.filterType == FilterType.ITEMS;
    }

    public boolean isFilterTypeFluid() {
        return this.filterType == FilterType.FLUIDS;
    }

    public boolean isBlacklistMode() {
        return this.filterMode == FilterMode.BLACKLIST;
    }

    public boolean isWhitelistMode() {
        return this.filterMode == FilterMode.WHITELIST;
    }

    public boolean usesCompare() {
        return this.compare != 0;
    }

    public boolean usesFilterMode() {
        return this.filterMode != FilterMode.UNDEFINED;
    }

    public boolean usesFilterType() {
        return this.filterType != FilterType.UNDEFINED;
    }

    public int getCompare() {
        if (compare == 0)
            throw new UnsupportedOperationException();
        return compare;
    }

    @Nonnull
    public FilterMode getFilterMode() {
        return filterMode;
    }

    @Nonnull
    public FilterType getFilterType() {
        return filterType;
    }

    @Nonnull
    public IItemHandlerModifiable getItemFilters() {
        if (this.filterType == FilterType.FLUIDS)
            throw new UnsupportedOperationException("FilterType does not allow item filters");
        return this.itemFilters;
    }

    @Nonnull
    public FluidInventory getFluidFilters() {
        if (this.filterType == FilterType.ITEMS)
            throw new UnsupportedOperationException("FilterType does not allow item filters");
        return this.fluidFilters;
    }

    /**
     * Reads all data from the given {@link NBTTagCompound} and applies it to this configuration
     */
    public void readFromNBT(NBTTagCompound tag) {
        //TODO: read legacy config of all network nodes, call read and write in NetworkNode#readConfig etc.
        //filter mode
        if (tag.hasKey("filterMode") && usesFilterMode()) {
            setFilterMode(FilterMode.values()[tag.getInteger("filterMode")]);
        }

        //compare
        if (tag.hasKey("compare") && usesCompare()) {
            setCompare(tag.getInteger("compare"));
        }

        //Filters
        if (tag.hasKey("type") && usesFilterType()) {
            FilterType type = FilterType.values()[tag.getInteger("type")];
            setFilterType(type);

            if (type == FilterType.ITEMS && tag.hasKey("items")) {
                NBTTagCompound itemTag = tag.getCompoundTag("items");
                IItemHandlerModifiable handler = getItemFilters();
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!itemTag.hasKey(i + ""))
                        continue;

                    handler.setStackInSlot(i, new ItemStack(itemTag.getCompoundTag(i + "")));
                }
            } else if (type == FilterType.FLUIDS && tag.hasKey("fluids")) {
                getFluidFilters().readFromNbt(tag.getCompoundTag("fluids"));
            }
        }

        invalidateCache();
    }

    /**
     * Serializes this configuration into nbt data.
     *
     * @param tag the tag to serialize this configuration to
     * @return the given {@code} tag parameter
     */
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        //filter mode
        if (usesFilterMode())
            tag.setInteger("filterMode", getFilterMode().ordinal());

        //compare
        if (usesCompare())
            tag.setInteger("compare", getCompare());

        //Filters
        if (usesFilterType()) {
            tag.setInteger("type", getFilterType().ordinal());
            if (this.filterType == FilterType.ITEMS) {
                NBTTagCompound itemTag = new NBTTagCompound();
                IItemHandlerModifiable handler = getItemFilters();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty())
                        continue;

                    NBTTagCompound stackTag = new NBTTagCompound();
                    stack.writeToNBT(stackTag);
                    itemTag.setTag(i + "", stackTag);
                }

                tag.setTag("items", itemTag);
            } else if (this.filterType == FilterType.FLUIDS) {
                tag.setTag("fluids", getFluidFilters().writeToNbt());
            }
        }

        return tag;
    }

    public enum FilterType {
        ITEMS,
        FLUIDS,
        ITEMS_AND_FLUIDS,
        UNDEFINED;
    }

    public enum FilterMode {
        WHITELIST,
        BLACKLIST,
        WHITELIST_AND_BLACKLIST,
        UNDEFINED;
    }

    public static class Builder {
        private final INetworkNode node;

        private FilterMode filterMode = FilterMode.UNDEFINED;
        private FilterMode initialFilterMode = FilterMode.UNDEFINED;

        private FilterType filterType = FilterType.UNDEFINED;
        private FilterType initialFilterType = FilterType.UNDEFINED;
        private int itemFilterSize = -1;
        private int fluidFilterSize = -1;
        private int compare = -1;

        public Builder(@Nonnull INetworkNode node) {
            this.node = node;
        }

        public Builder setInitialCompare(int compare) {
            this.compare = compare;
            return this;
        }

        public Builder compareDamageAndNbt() {
            this.compare = IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT;
            return this;
        }

        public Builder filterModeBlacklist() {
            this.initialFilterMode = FilterMode.BLACKLIST;
            return this;
        }

        public Builder filterModeWhitelist() {
            this.initialFilterMode = FilterMode.WHITELIST;
            return this;
        }

        public Builder allowedFilterModeBlacklist() {
            this.filterMode = FilterMode.BLACKLIST;
            this.initialFilterMode = FilterMode.BLACKLIST;
            return this;
        }

        public Builder allowedFilterModeWhitelist() {
            this.filterMode = FilterMode.WHITELIST;
            this.initialFilterMode = FilterMode.WHITELIST;
            return this;
        }

        public Builder allowedFilterModeBlackAndWhitelist() {
            this.filterMode = FilterMode.WHITELIST_AND_BLACKLIST;
            return this;
        }

        public Builder filterSizeNine() {
            this.itemFilterSize = 9;
            this.fluidFilterSize = 9;
            return this;
        }

        public Builder filterSizeOne() {
            this.itemFilterSize = 1;
            this.fluidFilterSize = 1;
            return this;
        }

        public Builder setFilterSize(int itemSize, int fluidSize) {
            this.itemFilterSize = itemSize;
            this.fluidFilterSize = fluidSize;
            return this;
        }

        public Builder filterTypeItems() {
            this.initialFilterType = FilterType.ITEMS;
            return this;
        }

        public Builder filterTypeFluids() {
            this.initialFilterType = FilterType.FLUIDS;
            return this;
        }

        public Builder allowedFilterTypeItems() {
            this.filterType = FilterType.ITEMS;
            this.initialFilterType = FilterType.ITEMS;
            return this;
        }

        public Builder allowedFilterTypeFluids() {
            this.filterType = FilterType.FLUIDS;
            this.initialFilterType = FilterType.FLUIDS;
            return this;
        }

        public Builder allowedFilterTypeItemsAndFluids() {
            this.filterType = FilterType.ITEMS_AND_FLUIDS;
            return this;
        }

        @Nonnull
        public RSTileConfiguration build() {
            return new RSTileConfiguration(this.node, this.filterType, this.itemFilterSize, this.fluidFilterSize, this.compare, this.filterMode, this.initialFilterMode);
        }
    }
}
