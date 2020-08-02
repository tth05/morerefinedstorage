package com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

public class ItemGridHandlerPortable implements IItemGridHandler {
    private final IPortableGrid portableGrid;
    private final IGrid grid;

    public ItemGridHandlerPortable(IPortableGrid portableGrid, IGrid grid) {
        this.portableGrid = portableGrid;
        this.grid = grid;
    }

    @Override
    public void onExtract(EntityPlayerMP player, ItemStack stack, int preferredSlot, int flags) {
        if (portableGrid.getStorage() == null || !grid.isActive())
            return;

        StackListEntry<ItemStack> entry =
                portableGrid.getItemCache().getList().getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
        if (entry != null)
            onExtract(player, entry.getId(), preferredSlot, flags);
    }

    @Override
    public void onExtract(EntityPlayerMP player, UUID id, int preferredSlot, int flags) {
        if (portableGrid.getStorage() == null || !grid.isActive()) {
            return;
        }

        StackListEntry<ItemStack> entry = portableGrid.getItemCache().getList().get(id);

        if (entry == null) {
            return;
        }

        long itemSize = entry.getCount();
        // We copy here because some mods change the NBT tag of an item after getting the stack limit
        int maxItemSize = entry.getStack().getItem().getItemStackLimit(entry.getStack().copy());

        boolean single = (flags & EXTRACT_SINGLE) == EXTRACT_SINGLE;

        ItemStack held = player.inventory.getItemStack();

        if (single) {
            if (!held.isEmpty() && (!API.instance().getComparer().isEqualNoQuantity(entry.getStack(), held) ||
                    held.getCount() + 1 > held.getMaxStackSize())) {
                return;
            }
        } else if (!player.inventory.getItemStack().isEmpty()) {
            return;
        }

        long size = 64;

        if ((flags & EXTRACT_HALF) == EXTRACT_HALF && itemSize > 1) {
            size = itemSize / 2;

            // Rationale for this check:
            // If we have 32 buckets, and we want to extract half, we expect/need to get 8 (max stack size 16 / 2).
            // Without this check, we would get 16 (total stack size 32 / 2).
            // Max item size also can't be 1. Otherwise, if we want to extract half of 8 lava buckets, we would get size 0 (1 / 2).
            if (size > maxItemSize / 2 && maxItemSize != 1) {
                size = maxItemSize / 2;
            }
        } else if (single) {
            size = 1;
        }

        size = Math.min(size, maxItemSize);

        // Do this before actually extracting, since portable grid sends updates as soon as a change happens (so before the storage tracker used to track)
        portableGrid.getItemStorageTracker().changed(player, entry.getStack().copy());

        StackListResult<ItemStack> took = portableGrid.getItemStorage()
                .extract(entry.getStack(), size, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT, Action.SIMULATE);

        if (took == null)
            return;

        if ((flags & EXTRACT_SHIFT) == EXTRACT_SHIFT) {
            IItemHandler playerInventory =
                    player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);

            if (playerInventory != null) {
                if (preferredSlot != -1) {
                    took.applyCount();
                    ItemStack remainder = playerInventory.insertItem(preferredSlot, took.getStack(), true);
                    if (remainder.getCount() != took.getCount()) {
                        StackListResult<ItemStack> inserted = portableGrid.getItemStorage()
                                .extract(entry.getStack(), size - remainder.getCount(),
                                        IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT, Action.PERFORM);
                        if(inserted != null)
                            inserted.applyCount();

                        playerInventory
                                .insertItem(preferredSlot, inserted == null ? ItemStack.EMPTY : inserted.getStack(), false);
                        took.setCount(remainder.getCount());
                        took.applyCount();
                    }
                }
                if (took.getCount() > 0 && ItemHandlerHelper.insertItemStacked(playerInventory, took.getStack(), true).isEmpty()) {
                    took = portableGrid.getItemStorage()
                            .extract(entry.getStack(), size, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT, Action.PERFORM);

                    if (took != null) {
                        took.applyCount();
                        ItemHandlerHelper.insertItemStacked(playerInventory, took.getStack(), false);
                    }
                }
            }
        } else {
            took = portableGrid.getItemStorage()
                    .extract(entry.getStack(), size, IComparer.COMPARE_DAMAGE | IComparer.COMPARE_NBT, Action.PERFORM);

            if (single && !held.isEmpty()) {
                held.grow(1);
            } else {
                if(took == null) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                } else {
                    took.applyCount();
                    player.inventory.setItemStack(took.getStack());
                }
            }

            player.updateHeldItem();
        }

        portableGrid.drainEnergy(RS.INSTANCE.config.portableGridExtractUsage);
    }

    @Nullable
    @Override
    public ItemStack onInsert(EntityPlayerMP player, ItemStack stack, boolean single) {
        if (portableGrid.getStorage() == null || !grid.isActive()) {
            return stack;
        }

        portableGrid.getItemStorageTracker().changed(player, stack.copy());

        ItemStack remainder;
        if (single) {
            if (portableGrid.getItemStorage().insert(stack, 1, Action.SIMULATE) == null) {
                portableGrid.getItemStorage().insert(stack, 1, Action.PERFORM);
                stack.shrink(1);
            }
            remainder = stack;
        } else {
            StackListResult<ItemStack> entry = portableGrid.getItemStorage().insert(stack, stack.getCount(), Action.PERFORM);
            if(entry == null)
                return null;
            entry.applyCount();
            remainder = entry.getStack();
        }

        portableGrid.drainEnergy(RS.INSTANCE.config.portableGridInsertUsage);

        return remainder;
    }

    @Override
    public void onInsertHeldItem(EntityPlayerMP player, boolean single) {
        if (player.inventory.getItemStack().isEmpty() || portableGrid.getStorage() == null || !grid.isActive()) {
            return;
        }

        ItemStack stack = player.inventory.getItemStack();
        int size = single ? 1 : stack.getCount();

        portableGrid.getItemStorageTracker().changed(player, stack.copy());

        if (single) {
            if (portableGrid.getItemStorage().insert(stack, size, Action.SIMULATE) == null) {
                portableGrid.getItemStorage().insert(stack, size, Action.PERFORM);

                stack.shrink(size);

                if (stack.getCount() == 0) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                }
            }
        } else {
            StackListResult<ItemStack> entry = portableGrid.getItemStorage().insert(stack, size, Action.PERFORM);
            if(entry == null) {
                player.inventory.setItemStack(ItemStack.EMPTY);
            } else {
                entry.applyCount();
                player.inventory.setItemStack(entry.getStack());
            }
        }

        player.updateHeldItem();

        portableGrid.drainEnergy(RS.INSTANCE.config.portableGridInsertUsage);
    }

    @Override
    public ItemStack onShiftClick(EntityPlayerMP player, ItemStack stack) {
        return StackUtils.nullToEmpty(onInsert(player, stack, false));
    }

    @Override
    public void onCraftingPreviewRequested(EntityPlayerMP player, UUID id, int quantity, boolean noPreview) {
        // NO OP
    }

    @Override
    public void onCraftingRequested(EntityPlayerMP player, UUID id, int quantity) {
        // NO OP
    }

    @Override
    public void onCraftingCancelRequested(EntityPlayerMP player, @Nullable UUID id) {
        // NO OP
    }
}
