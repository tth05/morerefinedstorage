package com.raoulvdberge.refinedstorage.apiimpl.network.grid;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.ICraftingGridBehavior;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridNetworkAware;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.util.*;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeGrid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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
                StackListResult<ItemStack> remainder = network.insertItem(slot, (long) slot.getCount(), Action.PERFORM);
                if (remainder != null) {
                    //give to player otherwise
                    giveToPlayerOrNetwork(slot, player, null);
                }

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
                    StackListResult<ItemStack> took = network.extractItem(possibility, 1L, IComparer.COMPARE_NBT |
                                    (possibility.getItem().isDamageable() ? 0 : IComparer.COMPARE_DAMAGE),
                            Action.PERFORM);

                    if (took != null) {
                        grid.getCraftingMatrix().setInventorySlotContents(i, took.getFixedStack());
                        network.getItemStorageTracker().changed(player, took.getFixedStack().copy());
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
            ((NetworkNodeGrid) grid).markNetworkNodeDirty();
        }
    }

    @Override
    public void onCrafted(IGridNetworkAware grid, IRecipe recipe, EntityPlayer player, @Nullable IStackList<ItemStack> availableItems, @Nullable IStackList<ItemStack> usedItems) {
        NonNullList<ItemStack> remainder = recipe.getRemainingItems(grid.getCraftingMatrix());

        INetwork network = grid.getNetwork();

        InventoryCrafting matrix = grid.getCraftingMatrix();

        for (int i = 0; i < grid.getCraftingMatrix().getSizeInventory(); ++i) {
            ItemStack slot = matrix.getStackInSlot(i);

            // Do we have a remainder?
            if (i < remainder.size() && !remainder.get(i).isEmpty()) {
                // If there is no space for the remainder, dump it in the player inventory.
                if (!slot.isEmpty() && slot.getCount() > 1) {
                    if (!player.inventory.addItemStackToInventory(remainder.get(i).copy())) { // If there is no space in the player inventory, try to dump it in the network.
                        ItemStack remainderStack = network == null ? remainder.get(i).copy() : network.insertItem(remainder.get(i).copy(), remainder.get(i).getCount(), Action.PERFORM);

                        // If there is no space in the network, just dump it in the world.
                        if (remainderStack != null && !remainderStack.isEmpty()) {
                            InventoryHelper.spawnItemStack(player.getEntityWorld(), player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), remainderStack);
                        }
                    }

                    matrix.decrStackSize(i, 1);
                } else {
                    matrix.setInventorySlotContents(i, remainder.get(i).copy());
                }
            } else if (!slot.isEmpty()) { // We don't have a remainder, but the slot is not empty.
                if (slot.getCount() == 1 && network != null) { // Attempt to refill the slot with the same item from the network, only if we have a network and only if it's the last item.
                    ItemStack refill;
                    if (availableItems == null) { // for regular crafting
                        refill = network.extractItem(slot, 1, Action.PERFORM);
                    } else { // for shift crafting
                        if (availableItems.getEntry(slot, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE) != null) {
                            refill = availableItems.remove(slot, 1).getFixedStack();
                            usedItems.add(refill);
                        } else {
                            refill = ItemStack.EMPTY;
                        }
                    }

                    matrix.setInventorySlotContents(i, refill);

                    if (!refill.isEmpty()) {
                        network.getItemStorageTracker().changed(player, refill.copy());
                    }
                } else { // We don't have a network, or, the slot still has more than 1 items in it. Just decrement then.
                    matrix.decrStackSize(i, 1);
                }
            }
        }

        grid.onCraftingMatrixChanged();
    }

    @Override
    public void onCraftedShift(IGridNetworkAware grid, EntityPlayer player) {
        InventoryCrafting matrix = grid.getCraftingMatrix();
        INetwork network = grid.getNetwork();
        List<ItemStack> craftedItemsList = new ArrayList<>();
        ItemStack crafted = grid.getCraftingResult().getStackInSlot(0);

        int maxCrafted = crafted.getMaxStackSize();

        int amountCrafted = 0;
        boolean useNetwork = network != null;

        IStackList<ItemStack> availableItems = null;
        if (useNetwork) {
            // We need a modifiable list of the items in storage that are relevant for this craft.
            // For performance reason we extract these into an extra list
            availableItems = createFilteredItemList(network, matrix);
        }

        //A second list to remember which items have been extracted
        IStackList<ItemStack> usedItems = API.instance().createItemStackList();

        ForgeHooks.setCraftingPlayer(player);
        // Do while the item is still craftable (aka is the result slot still the same as the original item?) and we don't exceed the max stack size.
        do {
            grid.onCrafted(player, availableItems, usedItems);

            craftedItemsList.add(crafted.copy());

            amountCrafted += crafted.getCount();
        } while (API.instance().getComparer().isEqual(crafted, grid.getCraftingResult().getStackInSlot(0)) && amountCrafted < maxCrafted && amountCrafted + crafted.getCount() <= maxCrafted);

        if (useNetwork) {
            usedItems.getStacks().forEach(stack -> network.extractItem(stack.getStack(), stack.getCount(), Action.PERFORM));
        }

        for (ItemStack craftedItem : craftedItemsList) {
            if (!player.inventory.addItemStackToInventory(craftedItem.copy())) {

                ItemStack remainder = craftedItem;

                if (useNetwork) {
                    remainder = network.insertItem(craftedItem, craftedItem.getCount(), Action.PERFORM);
                }

                if (remainder != null && !remainder.isEmpty()) {
                    InventoryHelper.spawnItemStack(player.getEntityWorld(), player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), remainder);
                }
            }
        }

        // @Volatile: This is some logic copied from CraftingResultSlot#onCrafting. We call this manually for shift clicking because
        // otherwise it's not being called.
        // For regular crafting, this is already called in ResultCraftingGridSlot#onTake -> onCrafting(stack)
        crafted.onCrafting(player.world, player, amountCrafted);
        FMLCommonHandler.instance().firePlayerCraftingEvent(player, ItemHandlerHelper.copyStackWithSize(crafted, amountCrafted), grid.getCraftingMatrix());
        ForgeHooks.setCraftingPlayer(null);
    }

    private IStackList<ItemStack> createFilteredItemList(INetwork network, InventoryCrafting matrix) {
        IStackList<ItemStack> availableItems = API.instance().createItemStackList();
        for (int i = 0; i < matrix.getSizeInventory(); ++i) {
            StackListEntry<ItemStack> entry = network.getItemStorageCache().getList().getEntry(matrix.getStackInSlot(i), IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);

            //Don't add the same item twice into the list. Items may appear twice in a recipe but not in storage.
            if (entry != null &&
                availableItems.getEntry(entry.getStack(), IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE) == null) {
                availableItems.add(entry.getStack(), entry.getCount());
            }
        }
        return availableItems;
    }


    @Override
    public void onClear(IGridNetworkAware grid, EntityPlayer player) {
        INetwork network = grid.getNetwork();
        InventoryCrafting matrix = grid.getCraftingMatrix();

        boolean useNetwork = network != null && grid.isActive() &&
                network.getSecurityManager().hasPermission(Permission.INSERT, player);

        if (matrix == null)
            return;

        for (int i = 0; i < matrix.getSizeInventory(); ++i) {
            ItemStack slot = matrix.getStackInSlot(i);

            if (slot.isEmpty())
                continue;

            if (useNetwork) {
                StackListResult<ItemStack> remainder = network.insertItem(slot, (long) slot.getCount(), Action.PERFORM);
                if (remainder != null)
                    giveToPlayerOrNetwork(remainder.getFixedStack(), player, null);
            } else {
                giveToPlayerOrNetwork(slot, player, null);
            }

            matrix.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    /**
     * This methods tries to give the given {@code itemStack} to the given {@code player} first and if that fails the
     * item is inserted into the given {@code network} or dropped on the ground.
     */
    private static void giveToPlayerOrNetwork(@Nonnull ItemStack itemStack, @Nonnull EntityPlayer player,
                                              @Nullable INetwork network) {
        if (!player.inventory.addItemStackToInventory(itemStack)) {
            //if the players inventory is full, insert the item into the network
            ItemStack remainingItem = itemStack;
            if (network != null) {
                StackListResult<ItemStack> result = network.insertItem(itemStack, (long) itemStack.getCount(), Action.PERFORM);

                if (result != null) {
                    remainingItem = result.getFixedStack();
                } else {
                    remainingItem = null;
                }
            }

            //if the network doesn't accept it, drop it into the world
            if (remainingItem != null) {
                InventoryHelper.spawnItemStack(player.getEntityWorld(), player.getPosition().getX(),
                        player.getPosition().getY(), player.getPosition().getZ(), remainingItem);
            } else if (network.getItemStorageTracker() != null) {
                network.getItemStorageTracker().changed(player, itemStack);
            }
        }
    }
}
