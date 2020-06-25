package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.api.network.grid.GridType;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridCraftingListener;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IFluidGridHandler;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.cache.IStorageCacheListener;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNodeGrid;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.container.slot.grid.SlotGridCrafting;
import com.raoulvdberge.refinedstorage.container.slot.grid.SlotGridCraftingResult;
import com.raoulvdberge.refinedstorage.container.slot.legacy.SlotLegacyBase;
import com.raoulvdberge.refinedstorage.container.slot.legacy.SlotLegacyDisabled;
import com.raoulvdberge.refinedstorage.container.slot.legacy.SlotLegacyFilter;
import com.raoulvdberge.refinedstorage.gui.IResizableDisplay;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import com.raoulvdberge.refinedstorage.tile.config.IType;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ContainerGrid extends ContainerBase implements IGridCraftingListener {
    private final IGrid grid;
    private IStorageCache cache;
    private IStorageCacheListener listener;
    private final IResizableDisplay display;

    private SlotGridCraftingResult craftingResultSlot;
    private SlotLegacyBase patternResultSlot;

    public ContainerGrid(IGrid grid, IResizableDisplay display, @Nullable TileBase gridTile, EntityPlayer player) {
        super(gridTile, player);

        this.grid = grid;
        this.display = display;

        initSlots();

        grid.addCraftingListener(this);
    }

    public void initSlots() {
        this.inventorySlots.clear();
        this.inventoryItemStacks.clear();

        this.transferManager.clearTransfers();

        addFilterSlots();

        if (grid instanceof IPortableGrid) {
            addPortableGridSlots();
        }

        if (grid.getGridType() == GridType.CRAFTING) {
            addCraftingSlots();
        } else if (grid.getGridType() == GridType.PATTERN) {
            addPatternSlots();
        }

        transferManager.setNotFoundHandler(slotIndex -> {
            if (!getPlayer().getEntityWorld().isRemote) {
                Slot slot = inventorySlots.get(slotIndex);

                if (grid instanceof IPortableGrid && slot instanceof SlotItemHandler &&
                        ((SlotItemHandler) slot).getItemHandler().equals(((IPortableGrid) grid).getDisk())) {
                    return ItemStack.EMPTY;
                }

                if (slot.getHasStack()) {
                    if (slot == craftingResultSlot) {
                        grid.onCraftedShift(getPlayer());
                    } else {
                        ItemStack stack = slot.getStack();

                        if (grid.getGridType() == GridType.FLUID) {
                            IFluidGridHandler fluidHandler = grid.getFluidHandler();

                            if (fluidHandler != null) {
                                slot.putStack(fluidHandler.onShiftClick((EntityPlayerMP) getPlayer(), stack));
                            }
                        } else {
                            IItemGridHandler itemHandler = grid.getItemHandler();

                            if (itemHandler != null) {
                                slot.putStack(itemHandler.onShiftClick((EntityPlayerMP) getPlayer(), stack));
                            } else if (slot instanceof SlotGridCrafting &&
                                    mergeItemStack(stack, 14, 14 + (9 * 4), false)) {
                                slot.onSlotChanged();

                                // This is needed because when a grid is disconnected,
                                // and a player shift clicks from the matrix to the inventory (this if case),
                                // the crafting inventory isn't being notified.
                                grid.onCraftingMatrixChanged();
                            }
                        }

                    }
                    detectAndSendChanges();
                }
            }

            return ItemStack.EMPTY;
        });

        addPlayerInventory(8, display.getYPlayerInventory());
    }

    private void addPortableGridSlots() {
        addSlotToContainer(new SlotItemHandler(((IPortableGrid) grid).getDisk(), 0, 204, 6));

        transferManager.addBiTransfer(getPlayer().inventory, ((IPortableGrid) grid).getDisk());
    }

    private void addFilterSlots() {
        int yStart = 6;

        if (grid instanceof IPortableGrid) {
            yStart = 38;
        }

        for (int i = 0; i < 4; ++i) {
            addSlotToContainer(new SlotItemHandler(grid.getFilter(), i, 204, yStart + (18 * i)));
        }

        transferManager.addBiTransfer(getPlayer().inventory, grid.getFilter());
    }

    private void addCraftingSlots() {
        int headerAndSlots = display.getTopHeight() + (display.getVisibleRows() * 18);

        int x = 26;
        int y = headerAndSlots + 4;

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotGridCrafting(grid.getCraftingMatrix(), i, x, y));

            x += 18;

            if ((i + 1) % 3 == 0) {
                y += 18;
                x = 26;
            }
        }
        craftingResultSlot =
                new SlotGridCraftingResult(this, getPlayer(), grid, 0, 130 + 4, headerAndSlots + 22);
        addSlotToContainer(craftingResultSlot);
    }

    private void addPatternSlots() {
        int headerAndSlots = display.getTopHeight() + (display.getVisibleRows() * 18);

        addSlotToContainer(new SlotItemHandler(((NetworkNodeGrid) grid).getPatterns(), 0, 172, headerAndSlots + 4));
        addSlotToContainer(new SlotItemHandler(((NetworkNodeGrid) grid).getPatterns(), 1, 172, headerAndSlots + 40));

        transferManager.addBiTransfer(getPlayer().inventory, ((NetworkNodeGrid) grid).getPatterns());

        // Processing patterns
        int ox = 8;
        int x = ox;
        int y = headerAndSlots + 4;

        for (int i = 0; i < 9 * 2; ++i) {
            addSlotToContainer(new SlotFilter(((NetworkNodeGrid) grid).getProcessingMatrix(), i, x, y,
                    SlotFilter.FILTER_ALLOW_SIZE).setEnableHandler(
                    () -> ((NetworkNodeGrid) grid).isProcessingPattern() &&
                            ((NetworkNodeGrid) grid).getType() == IType.ITEMS));
            addSlotToContainer(new SlotFilterFluid(((NetworkNodeGrid) grid).getProcessingMatrixFluids(), i, x, y,
                    SlotFilter.FILTER_ALLOW_SIZE).setEnableHandler(
                    () -> ((NetworkNodeGrid) grid).isProcessingPattern() &&
                            ((NetworkNodeGrid) grid).getType() == IType.FLUIDS));

            x += 18;

            if ((i + 1) % 3 == 0) {
                if (i == 8) {
                    ox = 98;
                    x = ox;
                    y = headerAndSlots + 4;
                } else {
                    x = ox;
                    y += 18;
                }
            }
        }

        // Regular patterns
        x = 26;
        y = headerAndSlots + 4;

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotLegacyFilter(grid.getCraftingMatrix(), i, x, y)
                    .setEnableHandler(() -> !((NetworkNodeGrid) grid).isProcessingPattern()));

            x += 18;

            if ((i + 1) % 3 == 0) {
                y += 18;
                x = 26;
            }
        }
        patternResultSlot = new SlotLegacyDisabled(grid.getCraftingResult(), 0, 134, headerAndSlots + 22)
                .setEnableHandler(() -> !((NetworkNodeGrid) grid).isProcessingPattern());
        addSlotToContainer(patternResultSlot);
    }

    public IGrid getGrid() {
        return grid;
    }

    @Override
    public void onCraftingMatrixChanged() {
        for (int i = 0; i < inventorySlots.size(); ++i) {
            Slot slot = inventorySlots.get(i);

            if (slot instanceof SlotGridCrafting || slot == craftingResultSlot || slot == patternResultSlot) {
                for (IContainerListener iContainerListener : listeners) {
                    // @Volatile: We can't use IContainerListener#sendSlotContents since EntityPlayerMP blocks SlotCrafting changes...
                    if (iContainerListener instanceof EntityPlayerMP) {
                        ((EntityPlayerMP) iContainerListener).connection
                                .sendPacket(new SPacketSetSlot(windowId, i, slot.getStack()));
                    }
                }
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (!getPlayer().world.isRemote) {
            // The grid is offline.
            if (grid.getStorageCache() == null) {
                // The grid just went offline, there is still a listener.
                if (listener != null) {
                    // Remove it from the previous cache and clean up.
                    cache.removeListener(listener);

                    listener = null;
                    cache = null;
                }
            } else if (listener == null) { // The grid came online.
                listener = grid.createListener((EntityPlayerMP) getPlayer());
                cache = grid.getStorageCache();

                cache.addListener(listener);
            }
        }

        super.detectAndSendChanges();
    }

    @Override
    public void onContainerClosed(@Nonnull EntityPlayer player) {
        super.onContainerClosed(player);

        if (!player.getEntityWorld().isRemote) {
            grid.onClosed(player);

            if (cache != null && listener != null) {
                cache.removeListener(listener);
            }
        }

        grid.removeCraftingListener(this);
    }

    @Override
    public boolean canMergeSlot(@Nonnull ItemStack stack, @Nonnull Slot slot) {
        if (slot == craftingResultSlot || slot == patternResultSlot) {
            return false;
        }

        return super.canMergeSlot(stack, slot);
    }

    @Override
    protected int getDisabledSlotNumber() {
        return grid.getSlotId();
    }
}
