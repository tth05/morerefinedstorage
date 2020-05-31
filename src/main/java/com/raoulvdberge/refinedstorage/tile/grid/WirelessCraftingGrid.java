package com.raoulvdberge.refinedstorage.tile.grid;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridCraftingListener;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.item.ItemWirelessCraftingGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class WirelessCraftingGrid extends WirelessGrid {

    public static int ID;
    private final int controllerDimension;
    private IRecipe currentRecipe;
    private final InventoryCrafting matrix;
    private final InventoryCraftResult result;
    private final Set<IGridCraftingListener> craftingListeners;

    public WirelessCraftingGrid(ItemStack stack, final boolean server, final int slotId) {
        super(stack, slotId);
        Container craftingContainer = new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
                return false;
            }

            @Override
            public void onCraftMatrixChanged(@Nonnull IInventory inventoryIn) {
                if (server) {
                    onCraftingMatrixChanged();
                }
            }
        };
        this.matrix = new InventoryCrafting(craftingContainer, 3, 3);
        this.result = new InventoryCraftResult();
        this.craftingListeners = new HashSet<>();
        this.controllerDimension = ItemWirelessCraftingGrid.getDimensionId(stack);
        if (stack.hasTagCompound()) {
            StackUtils.readItems(this.matrix, 1, stack.getTagCompound());
        }
    }

    public String getGuiTitle() {
        return "gui.refinedstorage:crafting_grid";
    }

    public GridType getGridType() {
        return GridType.CRAFTING;
    }

    public InventoryCrafting getCraftingMatrix() {
        return this.matrix;
    }

    public InventoryCraftResult getCraftingResult() {
        return this.result;
    }

    public void onCraftingMatrixChanged() {
        if (this.currentRecipe == null || !this.currentRecipe.matches(this.matrix, DimensionManager.getWorld(this.controllerDimension))) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(this.matrix, DimensionManager.getWorld(this.controllerDimension));
        }
        if (this.currentRecipe == null) {
            this.result.setInventorySlotContents(0, ItemStack.EMPTY);
        }
        else {
            this.result.setInventorySlotContents(0, this.currentRecipe.getCraftingResult(this.matrix));
        }
        this.craftingListeners.forEach(IGridCraftingListener::onCraftingMatrixChanged);
        if (!this.getStack().hasTagCompound()) {
            this.getStack().setTagCompound(new NBTTagCompound());
        }
        StackUtils.writeItems(this.matrix, 1, this.getStack().getTagCompound());
    }

    public void onCrafted(final EntityPlayer player) {
        final INetwork network = this.getNetwork();
        if (network != null) {
            network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessCraftingGridCraftUsage);
        }
        API.instance().getCraftingGridBehavior().onCrafted(this, this.currentRecipe, player);
    }

    public void onCraftedShift(final EntityPlayer player) {
        API.instance().getCraftingGridBehavior().onCraftedShift(this, this.currentRecipe, player);
    }

    public void onRecipeTransfer(final EntityPlayer player, final ItemStack[][] recipe) {
        API.instance().getCraftingGridBehavior().onRecipeTransfer(this, player, recipe);
    }

    @Override
    public void onClear(EntityPlayer player) {
        INetwork network = this.getNetwork();
        if (network != null && network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            for (int i = 0; i < matrix.getSizeInventory(); ++i) {
                ItemStack slot = matrix.getStackInSlot(i);

                if (!slot.isEmpty()) {
                    matrix.setInventorySlotContents(i, StackUtils.nullToEmpty(network.insertItem(slot, slot.getCount(), Action.PERFORM)));

                    network.getItemStorageTracker().changed(player, slot.copy());
                }
            }
        }
    }

    public void addCraftingListener(final IGridCraftingListener listener) {
        this.craftingListeners.add(listener);
    }

    public void removeCraftingListener(final IGridCraftingListener listener) {
        this.craftingListeners.remove(listener);
    }
}
