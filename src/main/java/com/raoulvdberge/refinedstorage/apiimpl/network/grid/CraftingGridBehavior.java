package com.raoulvdberge.refinedstorage.apiimpl.network.grid;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.ICraftingGridBehavior;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridNetworkAware;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CraftingGridBehavior implements ICraftingGridBehavior {

    @Override
    public void onRecipeTransfer(IGridNetworkAware grid, EntityPlayer player, ItemStack[][] recipe) {
        INetwork network = grid.getNetwork();

        if (network == null || grid.getCraftingMatrix() == null || (grid.getGridType() == GridType.CRAFTING &&
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

                    if (!took.isEmpty()) {
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
                grid.getCraftingMatrix()
                        .setInventorySlotContents(i, possibilities.length == 0 ? ItemStack.EMPTY : possibilities[0]);
            }
        }

        if (grid.getGridType() == GridType.PATTERN) {
            ((NetworkNodeGrid) grid).setProcessingPattern(false);
            ((NetworkNodeGrid) grid).markDirty();
        }
    }

    @Override
    public void onCrafted(IGridNetworkAware grid, IRecipe recipe, EntityPlayer player) {
        INetwork network = grid.getNetwork();

        InventoryCrafting matrix = grid.getCraftingMatrix();
        if (matrix == null)
            return;

        NonNullList<ItemStack> remainder = recipe.getRemainingItems(matrix);

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
    @Override
    public void onCraftedShift(IGridNetworkAware grid, IRecipe recipeUsed, EntityPlayer player) {
        INetwork network = grid.getNetwork();
        InventoryCrafting matrix = grid.getCraftingMatrix();
        //won't work anyway without all of these
        if (matrix == null || network == null || grid.getCraftingResult() == null ||
                grid.getStorageCache() == null || grid.getStorageCache().getList() == null || recipeUsed == null)
            return;

        ItemStack result = grid.getCraftingResult().getStackInSlot(0);
        NonNullList<ItemStack> remainder = recipeUsed.getRemainingItems(matrix);

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

            if (minCountStack.isItemStackDamageable())
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

                    if (correspondingSlot.isItemStackDamageable())
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
                public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
                    return false;
                }
            }, matrix.getWidth(), matrix.getHeight());
            for (Pair<Set<Pair<ItemStack, Integer>>, Boolean> commonSlotsPair : commonSlots) {
                for (Pair<ItemStack, Integer> commonSlotEntry : commonSlotsPair.getLeft())
                    matrixClone.setInventorySlotContents(commonSlotEntry.getRight(), commonSlotEntry.getLeft().copy());
            }

            Pair<Integer, NonNullList<ItemStack>> simulationResult =
                    simulateRemainder(toCraft, matrixClone, result, network.world());
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
            if (!remainingItem.isEmpty()) {
                InventoryHelper.spawnItemStack(player.getEntityWorld(), player.getPosition().getX(),
                        player.getPosition().getY(), player.getPosition().getZ(), remainingItem);
            } else if (network != null && network.getItemStorageTracker() != null) {
                network.getItemStorageTracker().changed(player, itemStack);
            }
        }
    }
}
