package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.api.network.grid.*;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IFluidGridHandler;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.IFilter;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.listener.StorageCacheListenerGridFluid;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.listener.StorageCacheListenerGridItem;
import com.raoulvdberge.refinedstorage.block.BlockGrid;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerFilter;
import com.raoulvdberge.refinedstorage.inventory.item.validator.ItemValidatorBasic;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemPattern;
import com.raoulvdberge.refinedstorage.tile.config.FilterType;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import com.raoulvdberge.refinedstorage.tile.grid.TileGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkNodeGrid extends NetworkNode implements IGridNetworkAware {
    public static final String ID = "grid";
    public static int FACTORY_ID;

    public static final String NBT_VIEW_TYPE = "ViewType";
    public static final String NBT_SORTING_DIRECTION = "SortingDirection";
    public static final String NBT_SORTING_TYPE = "SortingType";
    public static final String NBT_SEARCH_BOX_MODE = "SearchBoxMode";
    private static final String NBT_OREDICT_PATTERN = "OredictPattern";
    public static final String NBT_TAB_SELECTED = "TabSelected";
    public static final String NBT_TAB_PAGE = "TabPage";
    public static final String NBT_SIZE = "Size";
    private static final String NBT_PROCESSING_PATTERN = "ProcessingPattern";
    private static final String NBT_PROCESSING_TYPE = "ProcessingType";
    private static final String NBT_PROCESSING_MATRIX_FLUIDS = "ProcessingMatrixFluids";

    private final Container craftingContainer = new Container() {
        @Override
        public boolean canInteractWith(@Nonnull EntityPlayer player) {
            return false;
        }

        @Override
        public void onCraftMatrixChanged(@Nonnull IInventory inventory) {
            if (!world.isRemote) {
                onCraftingMatrixChanged();
            }
        }
    };
    private IRecipe currentRecipe;
    private final InventoryCrafting matrix = new InventoryCrafting(craftingContainer, 3, 3);
    private final InventoryCraftResult result = new InventoryCraftResult();
    private final ItemHandlerBase processingMatrix = new ItemHandlerBase(9 * 2, new ListenerNetworkNode(this));
    private final FluidInventory processingMatrixFluids =
            new FluidInventory(9 * 2, Fluid.BUCKET_VOLUME * 64, new ListenerNetworkNode(this));

    private final Set<IGridCraftingListener> craftingListeners = new HashSet<>();

    private final ItemHandlerBase patterns =
            new ItemHandlerBase(2, new ListenerNetworkNode(this), new ItemValidatorBasic(RSItems.PATTERN)) {
                @Override
                protected void onContentsChanged(int slot) {
                    super.onContentsChanged(slot);

                    ItemStack pattern = getStackInSlot(slot);
                    if (slot == 1 && !pattern.isEmpty()) {
                        boolean isProcessing = ItemPattern.isProcessing(pattern);

                        if (isProcessing) {
                            for (int i = 0; i < 9; ++i) {
                                processingMatrix.setStackInSlot(i,
                                        StackUtils.nullToEmpty(ItemPattern.getInputSlot(pattern, i)));
                                processingMatrixFluids.setFluid(i, ItemPattern.getFluidInputSlot(pattern, i));
                            }

                            for (int i = 0; i < 9; ++i) {
                                processingMatrix.setStackInSlot(9 + i,
                                        StackUtils.nullToEmpty(ItemPattern.getOutputSlot(pattern, i)));
                                processingMatrixFluids.setFluid(9 + i, ItemPattern.getFluidOutputSlot(pattern, i));
                            }
                        } else {
                            for (int i = 0; i < 9; ++i) {
                                matrix.setInventorySlotContents(i,
                                        StackUtils.nullToEmpty(ItemPattern.getInputSlot(pattern, i)));
                            }
                        }

                        setOredictPattern(ItemPattern.isOredict(pattern));
                        setProcessingPattern(isProcessing);
                        markNetworkNodeDirty();
                    }
                }

                @Override
                public int getSlotLimit(int slot) {
                    return slot == 1 ? 1 : super.getSlotLimit(slot);
                }

                @Nonnull
                @Override
                public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                    // Allow in slot 0
                    // Disallow in slot 1
                    // Only allow in slot 1 when it isn't a blank pattern
                    // This makes it so that written patterns can be re-inserted in slot 1 to be overwritten again
                    // This makes it so that blank patterns can't be inserted in slot 1 through hoppers.
                    if (slot == 0 || stack.getTagCompound() != null) {
                        return super.insertItem(slot, stack, simulate);
                    }

                    return stack;
                }
            };
    private final List<IFilter<?>> filters = new ArrayList<>();
    private final List<IGridTab> tabs = new ArrayList<>();
    private final ItemHandlerFilter filter = new ItemHandlerFilter(filters, tabs, new ListenerNetworkNode(this));

    private GridType type;

    private int viewType = VIEW_TYPE_NORMAL;
    private int sortingDirection = SORTING_DIRECTION_DESCENDING;
    private int sortingType = SORTING_TYPE_QUANTITY;
    private int searchBoxMode = SEARCH_BOX_MODE_NORMAL;
    private int size = SIZE_STRETCH;

    private int tabSelected = -1;
    private int tabPage = 0;

    private boolean oredictPattern = false;
    private boolean processingPattern = false;
    private FilterType processingType = FilterType.ITEMS;

    public NetworkNodeGrid(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        StackUtils.readItems(matrix, 0, tag);
        StackUtils.readItems(patterns, 1, tag);
        StackUtils.readItems(filter, 2, tag);
        StackUtils.readItems(processingMatrix, 3, tag);

        if (tag.hasKey(NBT_PROCESSING_MATRIX_FLUIDS)) {
            processingMatrixFluids.readFromNbt(tag.getCompoundTag(NBT_PROCESSING_MATRIX_FLUIDS));
        }

        if (tag.hasKey(NBT_TAB_SELECTED)) {
            tabSelected = tag.getInteger(NBT_TAB_SELECTED);
        }

        if (tag.hasKey(NBT_TAB_PAGE)) {
            tabPage = tag.getInteger(NBT_TAB_PAGE);
        }
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        StackUtils.writeItems(matrix, 0, tag);
        StackUtils.writeItems(patterns, 1, tag);
        StackUtils.writeItems(filter, 2, tag);
        StackUtils.writeItems(processingMatrix, 3, tag);

        tag.setTag(NBT_PROCESSING_MATRIX_FLUIDS, processingMatrixFluids.writeToNbt());
        tag.setInteger(NBT_TAB_SELECTED, tabSelected);
        tag.setInteger(NBT_TAB_PAGE, tabPage);

        return tag;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        tag.setInteger(NBT_VIEW_TYPE, viewType);
        tag.setInteger(NBT_SORTING_DIRECTION, sortingDirection);
        tag.setInteger(NBT_SORTING_TYPE, sortingType);
        tag.setInteger(NBT_SEARCH_BOX_MODE, searchBoxMode);
        tag.setInteger(NBT_SIZE, size);

        tag.setBoolean(NBT_OREDICT_PATTERN, oredictPattern);
        tag.setBoolean(NBT_PROCESSING_PATTERN, processingPattern);
        tag.setInteger(NBT_PROCESSING_TYPE, processingType.ordinal());

        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_VIEW_TYPE)) {
            viewType = tag.getInteger(NBT_VIEW_TYPE);
        }

        if (tag.hasKey(NBT_SORTING_DIRECTION)) {
            sortingDirection = tag.getInteger(NBT_SORTING_DIRECTION);
        }

        if (tag.hasKey(NBT_SORTING_TYPE)) {
            sortingType = tag.getInteger(NBT_SORTING_TYPE);
        }

        if (tag.hasKey(NBT_SEARCH_BOX_MODE)) {
            searchBoxMode = tag.getInteger(NBT_SEARCH_BOX_MODE);
        }

        if (tag.hasKey(NBT_SIZE)) {
            size = tag.getInteger(NBT_SIZE);
        }

        if (tag.hasKey(NBT_OREDICT_PATTERN)) {
            oredictPattern = tag.getBoolean(NBT_OREDICT_PATTERN);
        }

        if (tag.hasKey(NBT_PROCESSING_PATTERN)) {
            processingPattern = tag.getBoolean(NBT_PROCESSING_PATTERN);
        }

        if (tag.hasKey(NBT_PROCESSING_TYPE)) {
            processingType = FilterType.values()[tag.getInteger(NBT_PROCESSING_TYPE)];
        }
    }

    @Override
    public IStorageCacheListener<?> createListener(EntityPlayerMP player) {
        return type == GridType.FLUID ? new StorageCacheListenerGridFluid(player, network) :
                new StorageCacheListenerGridItem(player, network);
    }

    @Override
    public void addCraftingListener(IGridCraftingListener listener) {
        craftingListeners.add(listener);
    }

    @Override
    public void removeCraftingListener(IGridCraftingListener listener) {
        craftingListeners.remove(listener);
    }

    public void clearMatrix() {
        for (int i = 0; i < processingMatrix.getSlots(); ++i) {
            processingMatrix.setStackInSlot(i, ItemStack.EMPTY);
        }

        for (int i = 0; i < processingMatrixFluids.getSlots(); ++i) {
            processingMatrixFluids.setFluid(i, null);
        }

        for (int i = 0; i < matrix.getSizeInventory(); ++i) {
            matrix.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    public boolean canCreatePattern() {
        if (!isPatternAvailable()) {
            return false;
        }

        if (isProcessingPattern()) {
            int inputsFilled = 0;
            int outputsFilled = 0;

            for (int i = 0; i < 9; ++i) {
                if (!processingMatrix.getStackInSlot(i).isEmpty()) {
                    inputsFilled++;
                }

                if (processingMatrixFluids.getFluid(i) != null) {
                    inputsFilled++;
                }
            }

            for (int i = 9; i < 18; ++i) {
                if (!processingMatrix.getStackInSlot(i).isEmpty()) {
                    outputsFilled++;
                }

                if (processingMatrixFluids.getFluid(i) != null) {
                    outputsFilled++;
                }
            }

            return inputsFilled > 0 && outputsFilled > 0;
        } else {
            return !result.getStackInSlot(0).isEmpty() && isPatternAvailable();
        }
    }

    public void onCreatePattern() {
        if (canCreatePattern()) {
            if (patterns.getStackInSlot(1).isEmpty()) {
                patterns.extractItem(0, 1, false);
            }

            ItemStack pattern = new ItemStack(RSItems.PATTERN);

            ItemPattern.setVersion(pattern);
            ItemPattern.setOredict(pattern, oredictPattern);
            ItemPattern.setProcessing(pattern, processingPattern);

            if (processingPattern) {
                for (int i = 0; i < 18; ++i) {
                    if (!processingMatrix.getStackInSlot(i).isEmpty()) {
                        if (i >= 9) {
                            ItemPattern.setOutputSlot(pattern, i - 9, processingMatrix.getStackInSlot(i));
                        } else {
                            ItemPattern.setInputSlot(pattern, i, processingMatrix.getStackInSlot(i));
                        }
                    }

                    FluidStack fluid = processingMatrixFluids.getFluid(i);
                    if (fluid != null) {
                        if (i >= 9) {
                            ItemPattern.setFluidOutputSlot(pattern, i - 9, fluid);
                        } else {
                            ItemPattern.setFluidInputSlot(pattern, i, fluid);
                        }
                    }
                }
            } else {
                for (int i = 0; i < 9; ++i) {
                    ItemStack ingredient = matrix.getStackInSlot(i);

                    if (!ingredient.isEmpty()) {
                        ItemPattern.setInputSlot(pattern, i, ingredient);
                    }
                }
            }

            patterns.setStackInSlot(1, pattern);
        }
    }

    @Override
    public void onCraftingMatrixChanged() {
        if (currentRecipe == null || !currentRecipe.matches(matrix, world)) {
            currentRecipe = CraftingManager.findMatchingRecipe(matrix, world);
        }

        if (currentRecipe == null) {
            result.setInventorySlotContents(0, ItemStack.EMPTY);
        } else {
            result.setInventorySlotContents(0, currentRecipe.getCraftingResult(matrix));
        }

        craftingListeners.forEach(IGridCraftingListener::onCraftingMatrixChanged);

        markNetworkNodeDirty();
    }

    @Override
    public void onRecipeTransfer(EntityPlayer player, ItemStack[][] recipe) {
        API.instance().getCraftingGridBehavior().onRecipeTransfer(this, player, recipe);
    }

    @Override
    public void onCrafted(EntityPlayer player) {
        API.instance().getCraftingGridBehavior().onCrafted(this, currentRecipe, player);
    }

    @Override
    public void onCraftedShift(EntityPlayer player) {
        API.instance().getCraftingGridBehavior().onCraftedShift(this, currentRecipe, player);
    }

    @Override
    public void onClear(EntityPlayer player) {
        if (type == GridType.CRAFTING && network != null && network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            API.instance().getCraftingGridBehavior().onClear(this, player);
        } else if (type == GridType.PATTERN && network != null && network.canRun()) {
            clearMatrix();
        }
    }

    @Override
    public void onViewTypeChanged(int type) {
        TileDataManager.setParameter(TileGrid.VIEW_TYPE, type);
    }

    @Override
    public void onSortingTypeChanged(int type) {
        TileDataManager.setParameter(TileGrid.SORTING_TYPE, type);
    }

    @Override
    public void onSortingDirectionChanged(int direction) {
        TileDataManager.setParameter(TileGrid.SORTING_DIRECTION, direction);
    }

    @Override
    public void onSearchBoxModeChanged(int searchBoxMode) {
        TileDataManager.setParameter(TileGrid.SEARCH_BOX_MODE, searchBoxMode);
    }

    @Override
    public void onSizeChanged(int size) {
        TileDataManager.setParameter(TileGrid.SIZE, size);
    }

    @Override
    public void onTabSelectionChanged(int tab) {
        TileDataManager.setParameter(TileGrid.TAB_SELECTED, tab);
    }

    @Override
    public void onTabPageChanged(int page) {
        if (page >= 0 && page <= getTotalTabPages()) {
            TileDataManager.setParameter(TileGrid.TAB_PAGE, page);
        }
    }

    @Override
    public void onClosed(EntityPlayer player) {
        // NO OP
    }

    public void setProcessingType(FilterType processingType) {
        this.processingType = processingType;

        this.markNetworkNodeDirty();
    }

    public void setViewType(int viewType) {
        this.viewType = viewType;
    }

    public void setSortingDirection(int sortingDirection) {
        this.sortingDirection = sortingDirection;
    }

    public void setSortingType(int sortingType) {
        this.sortingType = sortingType;
    }

    public void setSearchBoxMode(int searchBoxMode) {
        this.searchBoxMode = searchBoxMode;
    }

    public void setTabSelected(int tabSelected) {
        this.tabSelected = tabSelected;
    }

    public void setTabPage(int page) {
        this.tabPage = page;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setOredictPattern(boolean oredictPattern) {
        this.oredictPattern = oredictPattern;
    }

    public void setProcessingPattern(boolean processingPattern) {
        this.processingPattern = processingPattern;
    }

    @Override
    public boolean hasConnectivityState() {
        return true;
    }

    public boolean isOredictPattern() {
        return oredictPattern;
    }

    public boolean isProcessingPattern() {
        return world.isRemote ? TileGrid.PROCESSING_PATTERN.getValue() : processingPattern;
    }

    private boolean isPatternAvailable() {
        return !(patterns.getStackInSlot(0).isEmpty() && patterns.getStackInSlot(1).isEmpty());
    }

    @Override
    public GridType getGridType() {
        if (type == null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == RSBlocks.GRID) {
                type = state.getValue(BlockGrid.TYPE);
            }
        }

        return type == null ? GridType.NORMAL : type;
    }

    @Nullable
    @Override
    public IStorageCache<?> getStorageCache() {
        return network != null ?
                (type == GridType.FLUID ? network.getFluidStorageCache() : network.getItemStorageCache()) :
                null;
    }

    @Nullable
    @Override
    public IItemGridHandler getItemHandler() {
        return network != null ? network.getItemGridHandler() : null;
    }

    @Nullable
    @Override
    public IFluidGridHandler getFluidHandler() {
        return network != null ? network.getFluidGridHandler() : null;
    }

    @Override
    public String getGuiTitle() {
        switch (type) {
            case CRAFTING:
                return "gui.refinedstorage:crafting_grid";
            case PATTERN:
                return "gui.refinedstorage:pattern_grid";
            case FLUID:
                return "gui.refinedstorage:fluid_grid";
            default:
                return "gui.refinedstorage:grid";
        }
    }

    public IItemHandler getPatterns() {
        return patterns;
    }

    @Override
    public IItemHandlerModifiable getFilter() {
        return filter;
    }

    @Override
    public List<IFilter<?>> getFilters() {
        return filters;
    }

    @Override
    public List<IGridTab> getTabs() {
        return tabs;
    }

    @Override
    public InventoryCrafting getCraftingMatrix() {
        return matrix;
    }

    @Override
    public InventoryCraftResult getCraftingResult() {
        return result;
    }

    public ItemHandlerBase getProcessingMatrix() {
        return processingMatrix;
    }

    public FluidInventory getProcessingMatrixFluids() {
        return processingMatrixFluids;
    }

    @Override
    public int getSlotId() {
        return -1;
    }

    @Override
    public int getViewType() {
        return world.isRemote ? TileGrid.VIEW_TYPE.getValue() : viewType;
    }

    @Override
    public int getSortingDirection() {
        return world.isRemote ? TileGrid.SORTING_DIRECTION.getValue() : sortingDirection;
    }

    @Override
    public int getSortingType() {
        return world.isRemote ? TileGrid.SORTING_TYPE.getValue() : sortingType;
    }

    @Override
    public int getSearchBoxMode() {
        return world.isRemote ? TileGrid.SEARCH_BOX_MODE.getValue() : searchBoxMode;
    }

    @Override
    public int getSize() {
        return world.isRemote ? TileGrid.SIZE.getValue() : size;
    }

    @Override
    public int getTabSelected() {
        return world.isRemote ? TileGrid.TAB_SELECTED.getValue() : tabSelected;
    }

    @Override
    public int getTabPage() {
        return world.isRemote ? TileGrid.TAB_PAGE.getValue() : Math.min(tabPage, getTotalTabPages());
    }

    @Override
    public int getTotalTabPages() {
        return (int) Math.floor((float) Math.max(0, tabs.size() - 1) / (float) IGrid.TABS_PER_PAGE);
    }

    public FilterType getProcessingType() {
        return world.isRemote ? FilterType.values()[TileGrid.PROCESSING_TYPE.getValue()] : processingType;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public IItemHandler getDrops() {
        switch (type) {
            case CRAFTING:
                return new CombinedInvWrapper(filter, new InvWrapper(matrix));
            case PATTERN:
                return new CombinedInvWrapper(filter, patterns);
            default:
                return new CombinedInvWrapper(filter);
        }
    }

    @Override
    public int getEnergyUsage() {
        switch (type) {
            case NORMAL:
                return RS.INSTANCE.config.gridUsage;
            case CRAFTING:
                return RS.INSTANCE.config.craftingGridUsage;
            case PATTERN:
                return RS.INSTANCE.config.patternGridUsage;
            case FLUID:
                return RS.INSTANCE.config.fluidGridUsage;
            default:
                return 0;
        }
    }
}
