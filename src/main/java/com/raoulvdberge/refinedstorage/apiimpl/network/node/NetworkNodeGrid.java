package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.*;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IFluidGridHandler;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
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
import com.raoulvdberge.refinedstorage.tile.config.IType;
import com.raoulvdberge.refinedstorage.tile.data.TileDataManager;
import com.raoulvdberge.refinedstorage.tile.grid.TileGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkNodeGrid extends NetworkNode implements IGridNetworkAware, IType {
    public static final ResourceLocation ID = new ResourceLocation(RS.ID, "grid");
    public static int FACTORY_ID = 0;

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

    private Container craftingContainer = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return false;
        }

        @Override
        public void onCraftMatrixChanged(IInventory inventory) {
            if (!world.isRemote) {
                onCraftingMatrixChanged();
            }
        }
    };
    private IRecipe currentRecipe;
    private InventoryCrafting matrix = new InventoryCrafting(craftingContainer, 3, 3);
    private InventoryCraftResult result = new InventoryCraftResult();
    private ItemHandlerBase processingMatrix = new ItemHandlerBase(9 * 2, new ListenerNetworkNode(this));
    private FluidInventory processingMatrixFluids =
            new FluidInventory(9 * 2, Fluid.BUCKET_VOLUME * 64, new ListenerNetworkNode(this));

    private Set<IGridCraftingListener> craftingListeners = new HashSet<>();

    private ItemHandlerBase patterns =
            new ItemHandlerBase(2, new ListenerNetworkNode(this), new ItemValidatorBasic(RSItems.PATTERN)) {
                @Override
                protected void onContentsChanged(int slot) {
                    super.onContentsChanged(slot);

                    ItemStack pattern = getStackInSlot(slot);
                    if (slot == 1 && !pattern.isEmpty()) {
                        boolean isPatternProcessing = ItemPattern.isProcessing(pattern);

                        if (isPatternProcessing && isProcessingPattern()) {
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
                        } else if (!isPatternProcessing && !isProcessingPattern()) {
                            for (int i = 0; i < 9; ++i) {
                                matrix.setInventorySlotContents(i,
                                        StackUtils.nullToEmpty(ItemPattern.getInputSlot(pattern, i)));
                            }
                        }
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
    private List<IFilter> filters = new ArrayList<>();
    private List<IGridTab> tabs = new ArrayList<>();
    private ItemHandlerFilter filter = new ItemHandlerFilter(filters, tabs, new ListenerNetworkNode(this));

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
    private int processingType = IType.ITEMS;

    public NetworkNodeGrid(World world, BlockPos pos) {
        super(world, pos);
    }

    public static void onRecipeTransfer(IGridNetworkAware grid, EntityPlayer player, ItemStack[][] recipe) {
        INetwork network = grid.getNetwork();

        if (network == null || (grid.getGridType() == GridType.CRAFTING &&
                !network.getSecurityManager().hasPermission(Permission.EXTRACT, player))) {
            return;
        }

        // First try to empty the crafting matrix
        for (int i = 0; i < grid.getCraftingMatrix().getSizeInventory(); ++i) {
            ItemStack slot = grid.getCraftingMatrix().getStackInSlot(i);

            // Only if we are a crafting grid. Pattern grids can just be emptied.
            if (!slot.isEmpty() && grid.getGridType() == GridType.CRAFTING) {
                // try to insert into network.
                giveToPlayerOrNetwork(slot, player, network);
                grid.getCraftingMatrix().setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }

        // Now let's fill the matrix
        matrixLoop:
        for (int i = 0; i < grid.getCraftingMatrix().getSizeInventory(); ++i) {
            ItemStack[] possibilities = recipe[i];
            if (possibilities == null)
                continue;

            // If we are a crafting grid
            if (grid.getGridType() == GridType.CRAFTING) {
                // If we are connected, first try to get the possibilities from the network
                for (ItemStack possibility : possibilities) {
                    ItemStack took = network.extractItem(possibility, 1, IComparer.COMPARE_NBT |
                                    (possibility.getItem().isDamageable() ? 0 : IComparer.COMPARE_DAMAGE),
                            Action.PERFORM);

                    if (took != null) {
                        grid.getCraftingMatrix().setInventorySlotContents(i, StackUtils.nullToEmpty(took));
                        network.getItemStorageTracker().changed(player, took.copy());
                        continue matrixLoop;
                    }
                }

                // If we haven't found anything in the network, go look in the player inventory
                outer:
                for (ItemStack possibility : possibilities) {
                    for (int j = 0; j < player.inventory.getSizeInventory(); ++j) {
                        ItemStack playerStack = player.inventory.getStackInSlot(j);
                        if (API.instance().getComparer().isEqual(possibility, playerStack,
                                IComparer.COMPARE_NBT |
                                        (possibility.getItem().isDamageable() ? 0 : IComparer.COMPARE_DAMAGE))) {
                            grid.getCraftingMatrix().setInventorySlotContents(i,
                                    ItemHandlerHelper.copyStackWithSize(playerStack, 1));

                            player.inventory.decrStackSize(j, 1);
                            break outer;
                        }
                    }
                }
            } else if (grid.getGridType() == GridType.PATTERN) {
                // If we are a pattern grid we can just set the slot
                grid.getCraftingMatrix().setInventorySlotContents(i, possibilities[0]);
            }
        }
    }

    public static void onCrafted(IGridNetworkAware grid, World world, EntityPlayer player) {
        NonNullList<ItemStack> remainder = CraftingManager.getRemainingItems(grid.getCraftingMatrix(), world);

        INetwork network = grid.getNetwork();

        InventoryCrafting matrix = grid.getCraftingMatrix();

        for (int i = 0; i < grid.getCraftingMatrix().getSizeInventory(); ++i) {
            ItemStack slot = matrix.getStackInSlot(i);

            //if there is a remainder
            if (i < remainder.size() && !remainder.get(i).isEmpty()) {
                //if the item that has a remainder is stacked, then there's no space for it in the crafting grid
                if (!slot.isEmpty() && slot.getCount() > 1) {
                    giveToPlayerOrNetwork(remainder.get(i).copy(), player, network);

                    matrix.decrStackSize(i, 1);
                } else {
                    //Replace item with remainder
                    matrix.setInventorySlotContents(i, remainder.get(i).copy());
                }
            } else if (!slot.isEmpty()) {
                if (slot.getCount() == 1 && network != null) {
                    //Refill one item so the slot is never empty if possible
                    ItemStack refill = StackUtils.nullToEmpty(network.extractItem(slot, 1, Action.PERFORM));

                    matrix.setInventorySlotContents(i, refill);

                    if (!refill.isEmpty())
                        network.getItemStorageTracker().changed(player, refill.copy());
                } else {
                    matrix.decrStackSize(i, 1);
                }
            }
        }

        grid.onCraftingMatrixChanged();
    }

    /**
     * Tries to craft the maximum amount possible using the given {@code grid}.
     * This code aims the reduce the INetwork#extractItem calls by as much as possible because this is the main
     * performance impact when having big systems.
     *
     * @param grid   the grid that should be used
     * @param player the player
     */
    public static void onCraftedShift(IGridNetworkAware grid, EntityPlayer player) {
        INetwork network = grid.getNetwork();
        InventoryCrafting matrix = grid.getCraftingMatrix();
        //won't work anyway without all of these
        if (matrix == null || network == null || grid.getCraftingResult() == null ||
                grid.getStorageCache() == null || grid.getStorageCache().getList() == null)
            return;

        IRecipe recipeUsed = CraftingManager.findMatchingRecipe(matrix, player.getEntityWorld());
        if (recipeUsed == null)
            return;

        ItemStack result = grid.getCraftingResult().getStackInSlot(0);
        NonNullList<ItemStack> remainder = CraftingManager.getRemainingItems(matrix, network.world());

        //the amount that can be crafted is limited by the stack size of the output at first
        int toCraft = result.getMaxStackSize() / result.getCount();

        //contains the amount that is in the network for each input slot
        List<Integer> networkCounts = new IntArrayList(matrix.getSizeInventory());
        List<Pair<ItemStack, Integer>> networkCountCache = new ArrayList<>(matrix.getSizeInventory());
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            ItemStack slot = matrix.getStackInSlot(i);
            if (slot.isEmpty()) {
                networkCounts.add(0);
                continue;
            }

            //check if item is cached
            Pair<ItemStack, Integer> cachedPair = networkCountCache.stream()
                    .filter(element -> API.instance().getComparer().isEqualNoQuantity(element.getLeft(), slot))
                    .findFirst().orElse(null);

            //cache network count which would result in a single request on the storage if all slots are equal
            int itemCountInNetwork;
            if (cachedPair != null) {
                itemCountInNetwork = cachedPair.getRight();
            } else {
                ItemStack networkItem = (ItemStack) grid.getStorageCache().getList().get(slot);
                itemCountInNetwork = networkItem == null ? 0 : networkItem.getCount();
                networkCountCache.add(Pair.of(slot, itemCountInNetwork));
            }

            networkCounts.add(itemCountInNetwork);
        }

        //contains already visited slots
        Set<Integer> seenSlots = new IntArraySet();
        //this is needed when there is an ItemStack that has one durability left, because then the remainder is empty
        // and the stack size is 1, which leads to the other code not detecting this. If this is true, then the
        // remainder simulation gets run, which limits toCraft correctly to 1.
        boolean foundDamageableItemStack = false;

        //contains sets of slots that are of the same type
        /*
        List ->
            Pair (
                Set ->
                    Pair (
                        A copy of the slot,
                        The slot index
                    )
                ),
                A boolean that determines whether or not all slots can be re-filled
            )
        */
        List<Pair<Set<Pair<ItemStack, Integer>>, Boolean>> commonSlots = new ArrayList<>();

        //this code further ensures that the maximum amount possible is crafted by splitting up items from the network
        // and limiting the crafted amount to the smallest stack size
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            if (seenSlots.contains(i))
                continue;
            ItemStack slot = matrix.getStackInSlot(i);
            if (slot.isEmpty())
                continue;
            seenSlots.add(i);

            //all slots containing the same ingredient
            Set<Pair<ItemStack, Integer>> correspondingSlots = new HashSet<>(matrix.getSizeInventory());

            //get the initial smallest stack
            ItemStack minCountStack = slot.copy();
            //calculate the amount of items that are missing to get all stacks to max size
            int missingCount = minCountStack.getMaxStackSize() - minCountStack.getCount();
            //add the origin slot at the start
            correspondingSlots.add(Pair.of(minCountStack, i));

            if(minCountStack.isItemStackDamageable())
                foundDamageableItemStack = true;

            for (int j = 0; j < matrix.getSizeInventory(); j++) {
                if (j == i || seenSlots.contains(j))
                    continue;
                ItemStack slot2 = matrix.getStackInSlot(j);

                //check if the slot has the same ingredient
                if (!slot2.isEmpty() && API.instance().getComparer().isEqualNoQuantity(slot, slot2)) {
                    ItemStack correspondingSlot = slot2.copy();
                    correspondingSlots.add(Pair.of(correspondingSlot, j));
                    seenSlots.add(j);

                    //missing count
                    missingCount +=
                            correspondingSlot.getMaxStackSize() - correspondingSlot.getCount();

                    //smallest stack
                    minCountStack = correspondingSlot.getCount() < minCountStack.getCount() ?
                            correspondingSlot : minCountStack;

                    if(correspondingSlot.isItemStackDamageable())
                        foundDamageableItemStack = true;
                }
            }

            int toSplitUp = networkCounts.get(i);

            //the second param is calculated later
            commonSlots.add(MutablePair.of(correspondingSlots, false));

            //if the max stack size is 1, ignore it
            if (slot.getMaxStackSize() == 1)
                continue;
            if (missingCount <= toSplitUp) {
                //fill up slot copies to maximum amount, otherwise the remainder simulation won't work
                for (Pair<ItemStack, Integer> correspondingSlot : correspondingSlots)
                    correspondingSlot.getLeft().setCount(correspondingSlot.getLeft().getMaxStackSize());
                continue;
            }

            //split up items evenly between all slots
            while (toSplitUp > 0) {
                minCountStack.grow(1);
                toSplitUp--;
                //recalculate smallest stack
                for (Pair<ItemStack, Integer> correspondingSlot : correspondingSlots)
                    minCountStack = correspondingSlot.getLeft().getCount() < minCountStack.getCount() ?
                            correspondingSlot.getLeft() : minCountStack;
            }

            //limit max crafted amount to the amount of the smallest stack
            toCraft = Math.min(toCraft, minCountStack.getCount());
        }

        NonNullList<ItemStack> finalRemainder;
        //if there is any remainder, then simulate the max iterations that can be done
        if (foundDamageableItemStack || remainder.stream().anyMatch(item -> !item.isEmpty())) {
            //create new matrix made up of the common slots
            InventoryCrafting matrixClone = new InventoryCrafting(new Container() {
                @Override
                public boolean canInteractWith(EntityPlayer playerIn) {
                    return false;
                }
            }, matrix.getWidth(), matrix.getHeight());
            for (Pair<Set<Pair<ItemStack, Integer>>, Boolean> commonSlotsPair : commonSlots) {
                for (Pair<ItemStack, Integer> commonSlotEntry : commonSlotsPair.getLeft())
                    matrixClone.setInventorySlotContents(commonSlotEntry.getRight(), commonSlotEntry.getLeft().copy());
            }

            Pair<Integer, NonNullList<ItemStack>> simulationResult =
                    simulateRemainder(toCraft, matrixClone, result, grid.getNetwork().world());
            toCraft = Math.min(toCraft, simulationResult.getLeft());
            //set remainder to the final remainder
            finalRemainder = simulationResult.getRight();
        } else {
            finalRemainder = remainder;
        }

        //remove items used in craft
        pairsLoop:
        for (Pair<Set<Pair<ItemStack, Integer>>, Boolean> commonSlotsPair : commonSlots) {
            //find all slots that need a re-fill and check if network can supply it
            int refillAmount = 0;

            int toExtract = 0;
            int networkCount = -1;
            //get total extraction amount
            for (Pair<ItemStack, Integer> commonSlotEntry : commonSlotsPair.getLeft()) {
                if (networkCount == -1)
                    networkCount = networkCounts.get(commonSlotEntry.getRight());

                int realStackCount = matrix.getStackInSlot(commonSlotEntry.getRight()).getCount();
                toExtract += Math.max(0, toCraft - realStackCount);

                //if stack would be emtpy
                if (realStackCount - toCraft < 1)
                    refillAmount++;
                //this means that the remainder gets placed into the slot and therefore the slot should not be
                // re-filled, nor should anything be extracted
                ItemStack correspondingRemainder = remainder.get(commonSlotEntry.getRight());
                ItemStack correspondingFinalRemainder = finalRemainder.get(commonSlotEntry.getRight());
                if (realStackCount == 1 && correspondingRemainder.getCount() <= 1 &&
                        !correspondingRemainder.isEmpty() &&
                        correspondingFinalRemainder.getCount() <= 1)
                    break pairsLoop;
            }

            //all found slots can be re-filled
            if (networkCount - toExtract - refillAmount >= 0) {
                commonSlotsPair.setValue(true);
                toExtract += refillAmount;
            }

            //remove used item from system
            if (toExtract > 0) {
                ItemStack extractedItem = StackUtils.nullToEmpty(
                        network.extractItem(commonSlotsPair.getLeft().iterator().next().getLeft(),
                                toExtract, Action.PERFORM));
                if (!extractedItem.isEmpty())
                    network.getItemStorageTracker().changed(player, extractedItem.copy());
            }
        }

        //re-fill and add remainder
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            ItemStack slot = matrix.getStackInSlot(i);
            if (slot.isEmpty())
                continue;

            //add remainder items
            ItemStack remainderItem = finalRemainder.get(i);
            if (i < finalRemainder.size() && !remainderItem.isEmpty()) {
                //Replace item with remainder if count is 1
                if (slot.getCount() == 1 && remainderItem.getCount() == 1) {
                    matrix.setInventorySlotContents(i, remainderItem);
                } else {
                    //split up remainder that shouldn't be stacked
                    do {
                        ItemStack newRemainderItem = remainderItem.splitStack(remainderItem.getMaxStackSize());
                        //if count is 2 we can't replace the item -> add it to the network or player inv
                        giveToPlayerOrNetwork(newRemainderItem, player, network);
                    } while (remainderItem.getCount() > 0);
                }
            }
            //only do this if remainder didn't replace item
            if (slot.getCount() != 1 || remainderItem.getCount() != 1) {
                if (slot.getCount() - toCraft < 1) { //refill from system if possible
                    boolean hasRefilledAllSlots = true;
                    outer:
                    for (Pair<Set<Pair<ItemStack, Integer>>, Boolean> commonSlotsPair : commonSlots) {
                        for (Pair<ItemStack, Integer> commonSlotEntry : commonSlotsPair.getLeft()) {
                            //if all slots can be re-filled, then the needed items already have been extracted.
                            if (commonSlotEntry.getRight() == i) {
                                hasRefilledAllSlots = commonSlotsPair.getRight();
                                break outer;
                            }
                        }
                    }

                    if (!hasRefilledAllSlots) {
                        ItemStack refill = StackUtils.nullToEmpty(network.extractItem(slot, 1, Action.PERFORM));
                        matrix.setInventorySlotContents(i, refill);
                        if (!refill.isEmpty())
                            network.getItemStorageTracker().changed(player, refill.copy());
                    } else {
                        matrix.decrStackSize(i, slot.getCount() - 1);
                    }
                } else { //decrease slot by crafted amount
                    matrix.decrStackSize(i, toCraft);
                }
            }
        }

        //create and insert crafted item
        ItemStack craftedItem = result.copy();
        craftedItem.setCount(toCraft * result.getCount());
        giveToPlayerOrNetwork(craftedItem, player, network);

        grid.onCraftingMatrixChanged();
        FMLCommonHandler.instance().firePlayerCraftingEvent(player, craftedItem.copy(), grid.getCraftingMatrix());
    }

    /**
     * @param maxIterations the max iterations that the simulation should run
     * @param matrix        the current crafting matrix
     * @param result        the current result
     * @return the maximum amount of iterations that can be run with the given {@code remainder} and the final remainder
     * after the run iterations
     */
    private static Pair<Integer, NonNullList<ItemStack>> simulateRemainder(int maxIterations,
                                                                           InventoryCrafting matrix,
                                                                           ItemStack result,
                                                                           World world) {
        int iterations = 0;

        //contains the total final remainder, items may exceed their max stack size
        NonNullList<ItemStack> finalRemainder = NonNullList.withSize(matrix.getSizeInventory(), ItemStack.EMPTY);
        Set<Integer> shouldGrowSlot = new IntArraySet(matrix.getSizeInventory());

        //loop until result changes
        do {
            iterations++;
            NonNullList<ItemStack> remainder = CraftingManager.getRemainingItems(matrix, world);
            for (int i = 0; i < remainder.size(); i++) {
                ItemStack itemStack = remainder.get(i);
                ItemStack stackInSlot = matrix.getStackInSlot(i);
                if (itemStack.isEmpty() && stackInSlot.isEmpty())
                    continue;
                //if there is a remainder
                if (!itemStack.isEmpty()) {
                    //if the item that has remainder is stacked, then remember that remainder item
                    if (!stackInSlot.isEmpty() && (stackInSlot.getCount() > 1 || shouldGrowSlot.contains(i))) {
                        ItemStack finalRemainderItemStack = finalRemainder.get(i);
                        if (finalRemainderItemStack.isEmpty()) {
                            finalRemainder.set(i, itemStack);
                            shouldGrowSlot.add(i);
                        } else {
                            finalRemainderItemStack.grow(1);
                        }
                        stackInSlot.shrink(1);
                    } else {
                        matrix.setInventorySlotContents(i, itemStack);
                        finalRemainder.set(i, itemStack);
                    }
                } else {
                    stackInSlot.shrink(1);
                }
            }
        } while (API.instance().getComparer().isEqual(result, CraftingManager.findMatchingResult(matrix, world)) &&
                iterations < maxIterations);

        return Pair.of(iterations, finalRemainder);
    }

    /**
     * This methods tries to give the given {@code itemStack} to the given {@code player} first and if that fails the
     * item is inserted into the given {@code network} or dropped on the ground.
     */
    //TODO: Move this to util/helper class
    private static void giveToPlayerOrNetwork(@Nonnull ItemStack itemStack, @Nonnull EntityPlayer player,
                                              @Nullable INetwork network) {
        if (!player.inventory.addItemStackToInventory(itemStack)) {
            //if the players inventory is full, insert the item into the network
            ItemStack remainingItem =
                    network == null ? itemStack : network.insertItem(itemStack, itemStack.getCount(), Action.PERFORM);

            //if the network doesn't accept it, drop it into the world
            if (remainingItem != null) {
                InventoryHelper.spawnItemStack(player.getEntityWorld(), player.getPosition().getX(),
                        player.getPosition().getY(), player.getPosition().getZ(), remainingItem);
            } else {
                network.getItemStorageTracker().changed(player, itemStack);
            }
        }
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
        tag.setInteger(NBT_PROCESSING_TYPE, processingType);

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
            processingType = tag.getInteger(NBT_PROCESSING_TYPE);
        }
    }

    @Override
    public IStorageCacheListener createListener(EntityPlayerMP player) {
        return getGridType() == GridType.FLUID ? new StorageCacheListenerGridFluid(player, network) :
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

        markDirty();
    }

    @Override
    public void onRecipeTransfer(EntityPlayer player, ItemStack[][] recipe) {
        onRecipeTransfer(this, player, recipe);
    }

    @Override
    public void onCrafted(EntityPlayer player) {
        onCrafted(this, world, player);
    }

    @Override
    public void onCraftedShift(EntityPlayer player) {
        onCraftedShift(this, player);
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

    @Override
    public void setType(int type) {
        this.processingType = type;

        this.markDirty();
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
                type = (GridType) state.getValue(BlockGrid.TYPE);
            }
        }

        return type == null ? GridType.NORMAL : type;
    }

    @Nullable
    @Override
    public IStorageCache getStorageCache() {
        return network != null ?
                (getGridType() == GridType.FLUID ? network.getFluidStorageCache() : network.getItemStorageCache()) :
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
        GridType type = getGridType();

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
    public List<IFilter> getFilters() {
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

    @Override
    public int getType() {
        return world.isRemote ? TileGrid.PROCESSING_TYPE.getValue() : processingType;
    }

    @Override
    public IItemHandlerModifiable getItemFilters() {
        return processingMatrix;
    }

    @Override
    public FluidInventory getFluidFilters() {
        return processingMatrixFluids;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public IItemHandler getDrops() {
        switch (getGridType()) {
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
        switch (getGridType()) {
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
