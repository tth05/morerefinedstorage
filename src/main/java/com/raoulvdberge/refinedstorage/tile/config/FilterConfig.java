package com.raoulvdberge.refinedstorage.tile.config;

import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeDetector;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeInterface;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.diskdrive.NetworkNodeDiskDrive;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.diskmanipulator.NetworkNodeDiskManipulator;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.storage.NetworkNodeFluidStorage;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameterClientListener;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

public class FilterConfig {
    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createFilterTypeParameter(@Nullable TileDataParameterClientListener<Integer> clientListener) {
        return new TileDataParameter<>(DataSerializers.VARINT, FilterType.ITEMS.ordinal(),
                t -> ((IRSFilterConfigProvider) t.getNode()).getConfig().getFilterType().ordinal(),
                (t, v) -> {
                    if (v < 0 || v >= FilterType.values().length)
                        return;
                    ((IRSFilterConfigProvider) t.getNode()).getConfig().setFilterType(FilterType.values()[v]);
                }, clientListener);
    }

    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createFilterTypeParameter() {
        return createFilterTypeParameter(null);
    }

    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createCompareParameter() {
        return new TileDataParameter<>(DataSerializers.VARINT, 0,
                t -> ((IRSFilterConfigProvider) t.getNode()).getConfig().getCompare(),
                (t, v) -> ((IRSFilterConfigProvider) t.getNode()).getConfig().setCompare(v)
        );
    }

    public static <T extends TileEntity & INetworkNodeProxy<?>> TileDataParameter<Integer, T> createFilterModeParameter() {
        return new TileDataParameter<>(DataSerializers.VARINT, 0,
                t -> ((IRSFilterConfigProvider) t.getNode()).getConfig().getFilterMode().ordinal(),
                (t, v) -> {
                    if (v < 0 || v >= FilterMode.values().length)
                        return;
                    ((IRSFilterConfigProvider) t.getNode()).getConfig().setFilterMode(FilterMode.values()[v]);
                });
    }

    private final INetworkNode node;

    //filters
    private final FilterType allowedFilterType;
    private FilterType filterType;
    private ItemHandlerBase itemFilters;
    private FluidInventory fluidFilters;
    private List<ItemStack> itemStacks;
    private List<FluidStack> fluidStacks;

    //compare
    private int compare;

    //filter mode
    private final FilterMode allowedFilterMode;
    private FilterMode filterMode;

    private IntConsumer itemFilterListener;
    private IntConsumer fluidFilterListener;

    private Function<FilterType, FilterType> customFilterTypeSupplier;
    private Consumer<FilterType> filterTypeChangedListener;

    private FilterConfig(@Nonnull INetworkNode node, int itemFilterSize, int fluidFilterSize, int initialCompare, @Nonnull FilterType allowedFilterType, @Nonnull FilterMode allowedFilterMode, @Nonnull FilterMode initialFilterMode, @Nonnull FilterType initialFilterType) {
        this.node = node;

        if (node == null)
            throw new IllegalArgumentException("null");

        if ((allowedFilterMode != FilterMode.UNDEFINED && initialFilterMode == FilterMode.UNDEFINED) ||
                (allowedFilterType != FilterType.UNDEFINED && initialFilterType == FilterType.UNDEFINED))
            throw new IllegalArgumentException("No default value supplied");

        if ((initialFilterMode != FilterMode.UNDEFINED && allowedFilterMode == FilterMode.UNDEFINED) ||
                (initialFilterType != FilterType.UNDEFINED && allowedFilterType == FilterType.UNDEFINED))
            throw new IllegalArgumentException("No allowed value supplied");

        if ((allowedFilterType == FilterType.ITEMS && initialFilterType != FilterType.ITEMS) ||
                (allowedFilterType == FilterType.FLUIDS && initialFilterType != FilterType.FLUIDS))
            throw new IllegalArgumentException("allowed filter type does not allow the given inital filter type");

        if ((allowedFilterMode == FilterMode.WHITELIST && initialFilterMode != FilterMode.WHITELIST) ||
                (allowedFilterMode == FilterMode.BLACKLIST && initialFilterMode != FilterMode.BLACKLIST))
            throw new IllegalArgumentException("allowed filter mode does not allow the given inital filter mode");

        this.allowedFilterType = allowedFilterType;
        this.allowedFilterMode = allowedFilterMode;
        this.filterMode = initialFilterMode;
        this.filterType = initialFilterType;
        this.compare = initialCompare;

        if (allowedFilterType == FilterType.ITEMS || allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
            if (itemFilterSize < 1)
                throw new IllegalArgumentException("Item filter size must be at least 1");

            this.itemFilters = new ItemHandlerBase(itemFilterSize, new ListenerNetworkNode(node)) {
                @Override
                protected void onContentsChanged(int slot) {
                    super.onContentsChanged(slot);
                    if (itemFilterListener != null)
                        itemFilterListener.accept(slot);

                    invalidateCache();
                }
            };
        }
        if (allowedFilterType == FilterType.FLUIDS || allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
            if (fluidFilterSize < 1)
                throw new IllegalArgumentException("Fluid filter size must be at least 1");

            this.fluidFilters = new FluidInventory(fluidFilterSize, new ListenerNetworkNode(node)) {
                @Override
                public void setFluid(int slot, @Nullable FluidStack stack) {
                    super.setFluid(slot, stack);
                    if (fluidFilterListener != null)
                        fluidFilterListener.accept(slot);

                    invalidateCache();
                }
            };
        }

        invalidateCache();
    }

    public boolean acceptsItem(@Nonnull ItemStack stack) {
        int compareValue = !usesCompare() ? IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT : getCompare();

        if (this.itemStacks == null || stack.isEmpty())
            return false;

        if (this.itemStacks.isEmpty())
            return isBlacklistMode();

        for (ItemStack filterStack : this.itemStacks) {
            if (API.instance().getComparer().isEqual(stack, filterStack, compareValue) == isWhitelistMode())
                return true;
        }

        return false;
    }

    public boolean acceptsFluid(@Nullable FluidStack stack) {
        int compareValue = !usesCompare() ? IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT : getCompare();

        if (this.fluidStacks == null || stack == null || stack.amount < 1)
            return false;

        if (this.fluidStacks.isEmpty())
            return isBlacklistMode();

        for (FluidStack filterStack : this.fluidStacks) {
            if (API.instance().getComparer().isEqual(stack, filterStack, compareValue) == isWhitelistMode())
                return true;
        }

        return false;
    }

    public void invalidateCache() {
        if (this.allowedFilterType == FilterType.ITEMS || this.allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
            this.itemStacks = new ArrayList<>();
            for (int i = 0; i < this.itemFilters.getSlots(); i++) {
                ItemStack stack = this.itemFilters.getStackInSlot(i);
                if (stack.isEmpty())
                    continue;

                this.itemStacks.add(stack);
            }

            this.itemStacks = Collections.unmodifiableList(this.itemStacks);
        } else {
            this.itemStacks = null;
        }

        if (this.allowedFilterType == FilterType.FLUIDS || this.allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
            this.fluidStacks = new ArrayList<>();
            for (int i = 0; i < this.fluidFilters.getSlots(); i++) {
                FluidStack stack = this.fluidFilters.getFluid(i);
                if (stack == null || stack.amount < 1)
                    continue;

                this.fluidStacks.add(stack);
            }

            this.fluidStacks = Collections.unmodifiableList(this.fluidStacks);
        } else {
            this.fluidStacks = null;
        }
    }

    public void setCompare(int compare) {
        if (compare < 0)
            throw new IllegalArgumentException();
        if (!usesCompare())
            throw new UnsupportedOperationException();
        this.compare = compare;

        this.node.markNetworkNodeDirty();
    }

    public void setFilterMode(@Nonnull FilterMode filterMode) {
        if (this.allowedFilterMode != FilterMode.WHITELIST_AND_BLACKLIST && this.filterMode != filterMode)
            throw new UnsupportedOperationException();
        this.filterMode = filterMode;

        this.node.markNetworkNodeDirty();
    }

    public void setFilterType(@Nonnull FilterType filterType) {
        if (this.allowedFilterType != FilterType.ITEMS_AND_FLUIDS && this.filterType != filterType)
            throw new UnsupportedOperationException();
        this.filterType = filterType;

        if (this.filterTypeChangedListener != null) {
            this.filterTypeChangedListener.accept(filterType);
        }

        this.node.markNetworkNodeDirty();
    }

    private void setItemFilterChangedListener(@Nullable IntConsumer itemFilterChangedListener) {
        this.itemFilterListener = itemFilterChangedListener;
    }

    private void setFluidFilterChangedListener(@Nullable IntConsumer fluidFilterChangedListener) {
        this.fluidFilterListener = fluidFilterChangedListener;
    }

    public void setCustomFilterTypeSupplier(@Nullable Function<FilterType, FilterType> customFilterTypeSupplier) {
        this.customFilterTypeSupplier = customFilterTypeSupplier;
    }

    public void setFilterTypeChangedListener(@Nullable Consumer<FilterType> onFilterTypeChanged) {
        this.filterTypeChangedListener = onFilterTypeChanged;
    }

    public boolean isItemFilterEmpty() {
        return this.itemStacks.isEmpty();
    }

    public boolean isFluidFilterEmpty() {
        return this.fluidStacks.isEmpty();
    }

    public boolean isFilterTypeItem() {
        return getFilterType() == FilterType.ITEMS;
    }

    public boolean isFilterTypeFluid() {
        return getFilterType() == FilterType.FLUIDS;
    }

    public boolean isBlacklistMode() {
        return this.filterMode == FilterMode.BLACKLIST;
    }

    public boolean isWhitelistMode() {
        return this.filterMode == FilterMode.WHITELIST;
    }

    public boolean usesCompare() {
        return this.compare != -1;
    }

    public boolean usesFilterMode() {
        return this.filterMode != FilterMode.UNDEFINED;
    }

    public boolean usesFilterType() {
        return getFilterType() != FilterType.UNDEFINED;
    }

    public int getCompare() {
        if (!usesCompare())
            throw new UnsupportedOperationException();
        return compare;
    }

    @Nonnull
    public FilterMode getFilterMode() {
        return filterMode;
    }

    @Nonnull
    public FilterType getFilterType() {
        if (this.customFilterTypeSupplier != null)
            return this.customFilterTypeSupplier.apply(this.filterType);
        return filterType;
    }

    @Nonnull
    public IItemHandlerModifiable getItemHandler() {
        if (this.allowedFilterType == FilterType.FLUIDS || !usesFilterType())
            throw new UnsupportedOperationException("current filter type does not allow item filters");
        return this.itemFilters;
    }

    @Nonnull
    public List<ItemStack> getItemFilters() {
        if (this.allowedFilterType == FilterType.FLUIDS || !usesFilterType())
            throw new UnsupportedOperationException("current filter type does not allow item filters");
        return this.itemStacks;
    }

    @Nonnull
    public FluidInventory getFluidHandler() {
        if (this.allowedFilterType == FilterType.ITEMS || !usesFilterType())
            throw new UnsupportedOperationException("current filter type does not allow fluid filters");
        return this.fluidFilters;
    }

    @Nonnull
    public List<FluidStack> getFluidFilters() {
        if (this.allowedFilterType == FilterType.ITEMS || !usesFilterType())
            throw new UnsupportedOperationException("current filter type does not allow fluid filters");
        return this.fluidStacks;
    }

    private void readFromNBTOld(NBTTagCompound tag) {
        if (tag.hasKey("Compare")) {
            this.compare = tag.getInteger("Compare");
        }

        if (tag.hasKey("Mode")) {
            //detector uses Mode for something else
            if (!(this.node instanceof NetworkNodeDetector)) {
                this.setFilterMode(FilterMode.values()[tag.getInteger("Mode")]);
            }
        }

        if (tag.hasKey("Type")) {
            this.filterType = FilterType.values()[tag.getInteger("Type")];
        }

        String oldFilterKey = this.node instanceof NetworkNodeFluidStorage ? "Filters" : "FluidFilters";
        if (tag.hasKey(oldFilterKey)) {
            this.fluidFilters.readFromNbt(tag.getCompoundTag(oldFilterKey));
        }

        int inventoryId = this.node instanceof NetworkNodeInterface ||
                this.node instanceof NetworkNodeDiskManipulator ||
                this.node instanceof NetworkNodeDiskDrive ? 1 : 0;
        if (tag.hasKey("Inventory_" + inventoryId)) {
            StackUtils.readItems(this.itemFilters, inventoryId, tag);
        }

        invalidateCache();
    }

    /**
     * Reads all data from the given {@link NBTTagCompound} and applies it to this configuration
     */
    public void readFromNBT(NBTTagCompound tag) {
        String oldInventoryKey = "Inventory_" + (this.node instanceof NetworkNodeInterface ||
                this.node instanceof NetworkNodeDiskManipulator ||
                this.node instanceof NetworkNodeDiskDrive ? 1 : 0);
        String oldFilterKey = this.node instanceof NetworkNodeFluidStorage ? "Filters" : "FluidFilters";
        if (tag.hasKey("Compare") || tag.hasKey(oldInventoryKey) ||
                tag.hasKey("Mode") || tag.hasKey("Type") || tag.hasKey(oldFilterKey)) {
            readFromNBTOld(tag);
            return;
        }

        if (!tag.hasKey("config"))
            return;

        tag = tag.getCompoundTag("config");

        //filter mode
        if (tag.hasKey("filterMode") && usesFilterMode() && this.allowedFilterMode == FilterMode.WHITELIST_AND_BLACKLIST) {
            setFilterMode(FilterMode.values()[tag.getInteger("filterMode")]);
        }

        //compare
        if (tag.hasKey("compare") && usesCompare()) {
            setCompare(tag.getInteger("compare"));
        }

        //Filters
        if (tag.hasKey("type") && usesFilterType()) {
            FilterType type = FilterType.values()[tag.getInteger("type")];
            if (this.allowedFilterType == FilterType.ITEMS_AND_FLUIDS)
                setFilterType(type);

            if ((allowedFilterType == FilterType.ITEMS || allowedFilterType == FilterType.ITEMS_AND_FLUIDS) && tag.hasKey("items")) {
                NBTTagCompound itemTag = tag.getCompoundTag("items");
                IItemHandlerModifiable handler = getItemHandler();
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!itemTag.hasKey(i + ""))
                        continue;

                    handler.setStackInSlot(i, new ItemStack(itemTag.getCompoundTag(i + "")));
                }
            }
            if ((allowedFilterType == FilterType.FLUIDS || allowedFilterType == FilterType.ITEMS_AND_FLUIDS) && tag.hasKey("fluids")) {
                getFluidHandler().readFromNbt(tag.getCompoundTag("fluids"));
            }
        }

        invalidateCache();
    }

    /**
     * Serializes this configuration into nbt data.
     *
     * @param mainTag the tag to serialize this configuration to
     * @return the given {@code} tag parameter
     */
    public NBTTagCompound writeToNBT(NBTTagCompound mainTag) {
        NBTTagCompound tag = new NBTTagCompound();

        //filter mode
        if (usesFilterMode())
            tag.setInteger("filterMode", getFilterMode().ordinal());

        //compare
        if (usesCompare())
            tag.setInteger("compare", getCompare());

        //Filters
        if (usesFilterType()) {
            tag.setInteger("type", getFilterType().ordinal());
            if (this.allowedFilterType == FilterType.ITEMS || this.allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
                NBTTagCompound itemTag = new NBTTagCompound();
                IItemHandlerModifiable handler = getItemHandler();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty())
                        continue;

                    NBTTagCompound stackTag = new NBTTagCompound();
                    stack.writeToNBT(stackTag);
                    itemTag.setTag(i + "", stackTag);
                }

                tag.setTag("items", itemTag);
            }

            if (this.allowedFilterType == FilterType.FLUIDS || this.allowedFilterType == FilterType.ITEMS_AND_FLUIDS) {
                tag.setTag("fluids", getFluidHandler().writeToNbt());
            }
        }

        mainTag.setTag("config", tag);
        return mainTag;
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

        private IntConsumer itemFilterChangedListener;
        private IntConsumer fluidFilterChangedListener;

        private Function<FilterType, FilterType> customFilterTypeSupplier;
        private Consumer<FilterType> filterTypeChangedListener;

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

        public Builder onItemFilterChanged(@Nonnull IntConsumer consumer) {
            this.itemFilterChangedListener = consumer;
            return this;
        }

        public Builder onFluidFilterChanged(@Nonnull IntConsumer consumer) {
            this.fluidFilterChangedListener = consumer;
            return this;
        }

        public Builder customFilterTypeSupplier(@Nonnull Function<FilterType, FilterType> supplier) {
            this.customFilterTypeSupplier = supplier;
            return this;
        }

        public Builder onFilterTypeChanged(@Nonnull Consumer<FilterType> consumer) {
            this.filterTypeChangedListener = consumer;
            return this;
        }

        @Nonnull
        public FilterConfig build() {
            FilterConfig filterConfig = new FilterConfig(this.node, this.itemFilterSize, this.fluidFilterSize, this.compare, this.filterType, this.filterMode, this.initialFilterMode, this.initialFilterType);
            filterConfig.setItemFilterChangedListener(this.itemFilterChangedListener);
            filterConfig.setFluidFilterChangedListener(this.fluidFilterChangedListener);
            filterConfig.setCustomFilterTypeSupplier(this.customFilterTypeSupplier);
            filterConfig.setFilterTypeChangedListener(this.filterTypeChangedListener);
            return filterConfig;
        }

    }
}
