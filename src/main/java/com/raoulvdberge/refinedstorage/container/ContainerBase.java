package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.container.slot.legacy.SlotLegacyDisabled;
import com.raoulvdberge.refinedstorage.container.slot.legacy.SlotLegacyFilter;
import com.raoulvdberge.refinedstorage.container.transfer.TransferManager;
import com.raoulvdberge.refinedstorage.network.MessageSlotFilterFluidUpdate;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import com.raoulvdberge.refinedstorage.tile.data.TileDataWatcher;
import invtweaks.api.container.InventoryContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@InventoryContainer(showOptions = false)
public abstract class ContainerBase extends Container {
    @Nullable
    private final TileBase tile;
    @Nullable
    private TileDataWatcher listener;
    private final EntityPlayer player;

    protected final TransferManager transferManager = new TransferManager(this);

    private final List<SlotFilterFluid> fluidSlots = new ArrayList<>();
    private final List<FluidStack> fluids = new ArrayList<>();

    public ContainerBase(@Nullable TileBase tile, EntityPlayer player) {
        this.tile = tile;

        if (tile != null && player instanceof EntityPlayerMP) {
            listener = new TileDataWatcher((EntityPlayerMP) player, tile.getDataManager());
        }

        this.player = player;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    @Nullable
    public TileBase getTile() {
        return tile;
    }

    protected void addPlayerInventory(int xInventory, int yInventory) {
        int disabledSlotNumber = getDisabledSlotNumber();

        int id = 9;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                if (id == disabledSlotNumber) {
                    addSlotToContainer(new SlotLegacyDisabled(player.inventory, id, xInventory + x * 18, yInventory + y * 18));
                } else {
                    addSlotToContainer(new Slot(player.inventory, id, xInventory + x * 18, yInventory + y * 18));
                }

                id++;
            }
        }

        id = 0;

        for (int i = 0; i < 9; i++) {
            int x = xInventory + i * 18;
            int y = yInventory + 4 + (3 * 18);

            if (id == disabledSlotNumber) {
                addSlotToContainer(new SlotLegacyDisabled(player.inventory, id, x, y));
            } else {
                addSlotToContainer(new Slot(player.inventory, id, x, y));
            }

            id++;
        }
    }

    public List<SlotFilterFluid> getFluidSlots() {
        return fluidSlots;
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int id, int dragType, @Nonnull ClickType clickType, @Nonnull EntityPlayer player) {
        Slot slot = id >= 0 ? getSlot(id) : null;

        int disabledSlotNumber = getDisabledSlotNumber();

        // Prevent swapping disabled held item with the number keys (dragType is the slot we're swapping with)
        if (disabledSlotNumber != -1 &&
                clickType == ClickType.SWAP &&
                dragType == disabledSlotNumber) {
            return ItemStack.EMPTY;
        }

        if (slot instanceof SlotFilter) {
            if (((SlotFilter) slot).isSizeAllowed()) {
                if (clickType == ClickType.QUICK_MOVE) {
                    slot.putStack(ItemStack.EMPTY);
                } else if (!player.inventory.getItemStack().isEmpty()) {
                    slot.putStack(player.inventory.getItemStack().copy());
                }
            } else if (player.inventory.getItemStack().isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else if (slot.isItemValid(player.inventory.getItemStack())) {
                slot.putStack(player.inventory.getItemStack().copy());
            }

            return player.inventory.getItemStack();
        } else if (slot instanceof SlotFilterFluid) {
            if (((SlotFilterFluid) slot).isSizeAllowed()) {
                if (clickType == ClickType.QUICK_MOVE) {
                    ((SlotFilterFluid) slot).onContainerClicked(ItemStack.EMPTY);
                } else if (!player.inventory.getItemStack().isEmpty()) {
                    ((SlotFilterFluid) slot).onContainerClicked(player.inventory.getItemStack());
                }
            } else if (player.inventory.getItemStack().isEmpty()) {
                ((SlotFilterFluid) slot).onContainerClicked(ItemStack.EMPTY);
            } else {
                ((SlotFilterFluid) slot).onContainerClicked(player.inventory.getItemStack());
            }

            return player.inventory.getItemStack();
        } else if (slot instanceof SlotLegacyFilter) {
            if (player.inventory.getItemStack().isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else if (slot.isItemValid(player.inventory.getItemStack())) {
                slot.putStack(player.inventory.getItemStack().copy());
            }

            return player.inventory.getItemStack();
        } else if (slot instanceof SlotLegacyDisabled) {
            return ItemStack.EMPTY;
        }

        return super.slotClick(id, dragType, clickType, player);
    }

    @Nonnull
    @Override
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer player, int slotIndex) {
        return transferManager.transfer(slotIndex);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return true;
    }

    @Override
    public boolean canMergeSlot(@Nonnull ItemStack stack, @Nonnull Slot slot) {
        if (slot instanceof SlotFilter || slot instanceof SlotFilterFluid || slot instanceof SlotLegacyFilter) {
            return false;
        }

        return super.canMergeSlot(stack, slot);
    }

    protected int getDisabledSlotNumber() {
        return -1;
    }

    @Nonnull
    @Override
    protected Slot addSlotToContainer(@Nonnull Slot slot) {
        if (slot instanceof SlotFilterFluid) {
            fluids.add(null);
            fluidSlots.add((SlotFilterFluid) slot);
        }

        return super.addSlotToContainer(slot);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (listener != null) {
            listener.detectAndSendChanges();
        }

        if (this.getPlayer() instanceof EntityPlayerMP) {
            for (int i = 0; i < this.fluidSlots.size(); ++i) {
                SlotFilterFluid slot = this.fluidSlots.get(i);

                FluidStack cached = this.fluids.get(i);
                FluidStack actual = slot.getFluidInventory().getFluid(slot.getSlotIndex());

                if (!API.instance().getComparer().isEqual(cached, actual, IComparer.COMPARE_QUANTITY | IComparer.COMPARE_NBT)) {
                    this.fluids.set(i, actual);

                    RS.INSTANCE.network.sendTo(new MessageSlotFilterFluidUpdate(slot.slotNumber, actual), (EntityPlayerMP) this.getPlayer());
                }
            }
        }
    }

    @Override
    public void onContainerClosed(@Nonnull EntityPlayer player) {
        super.onContainerClosed(player);

        if (listener != null) {
            listener.onClosed();
        }
    }
}
