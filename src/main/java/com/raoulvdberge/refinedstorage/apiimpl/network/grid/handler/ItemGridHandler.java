package com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.CraftingManager;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementError;
import com.raoulvdberge.refinedstorage.network.MessageGridCraftingPreviewResponse;
import com.raoulvdberge.refinedstorage.network.MessageGridCraftingStartResponse;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;

public class ItemGridHandler implements IItemGridHandler {
    private final INetwork network;

    public ItemGridHandler(INetwork network) {
        this.network = network;
    }

    @Override
    public void onExtract(EntityPlayerMP player, ItemStack stack, int preferredSlot, int flags) {
        StackListEntry<ItemStack> entry =
                network.getItemStorageCache().getList()
                        .getEntry(stack, IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE);
        if (entry != null)
            onExtract(player, entry.getId(), preferredSlot, flags);
    }

    @Override
    public void onExtract(EntityPlayerMP player, UUID id, int preferredSlot, int flags) {
        StackListEntry<ItemStack> entry = network.getItemStorageCache().getList().get(id);

        if (entry == null || !network.getSecurityManager().hasPermission(Permission.EXTRACT, player)) {
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

        // Do this before actually extracting, since external storage sends updates as soon as a change happens (so before the storage tracker used to track)
        network.getItemStorageTracker().changed(player, entry.getStack().copy());

        StackListResult<ItemStack> took = network.extractItem(entry.getStack(), size, Action.SIMULATE);

        if (took == null)
            return;
        took.applyCount();

        if ((flags & EXTRACT_SHIFT) == EXTRACT_SHIFT) {
            IItemHandler playerInventory =
                    player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (playerInventory != null) {
                if (preferredSlot != -1) {
                    ItemStack remainder = playerInventory.insertItem(preferredSlot, took.getStack(), true);
                    if (remainder.getCount() != took.getCount()) {
                        StackListResult<ItemStack> inserted = network.extractItem(entry.getStack(), size - remainder.getCount(), Action.PERFORM);
                        if(inserted != null) {
                            inserted.applyCount();
                            playerInventory.insertItem(preferredSlot, inserted.getStack(), false);
                        } else {
                            playerInventory.insertItem(preferredSlot, ItemStack.EMPTY, false);
                        }

                        took.setCount(remainder.getCount());
                        took.applyCount();
                    }
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(playerInventory, took.getStack(), false);

                if (took.getCount() - remainder.getCount() > 0)
                    network.extractItem(entry.getStack(), took.getCount() - remainder.getCount(), Action.PERFORM);
            }
        } else {
            took = network.extractItem(entry.getStack(), size, Action.PERFORM);

            if (took != null) {
                if (single && !held.isEmpty()) {
                    held.grow(1);
                } else {
                    took.applyCount();
                    player.inventory.setItemStack(took.getStack());
                }

                player.updateHeldItem();
            }
        }

        network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessGridExtractUsage);
    }

    @Override
    public ItemStack onInsert(EntityPlayerMP player, ItemStack stack, boolean single) {
        if (!network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            return stack;
        }

        network.getItemStorageTracker().changed(player, stack.copy());

        ItemStack remainder;
        if (single) {
            if (network.insertItem(stack, 1, Action.SIMULATE) == null) {
                network.insertItem(stack, 1, Action.PERFORM);
                stack.shrink(1);
            }
            remainder = stack;
        } else {
            remainder = network.insertItem(stack, stack.getCount(), Action.PERFORM);
        }

        network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessGridInsertUsage);

        return remainder;
    }

    @Override
    public void onInsertHeldItem(EntityPlayerMP player, boolean single) {
        if (player.inventory.getItemStack().isEmpty() ||
                !network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            return;
        }

        ItemStack stack = player.inventory.getItemStack();
        int size = single ? 1 : stack.getCount();

        network.getItemStorageTracker().changed(player, stack.copy());

        if (single) {
            if (network.insertItem(stack, size, Action.SIMULATE) == null) {
                network.insertItem(stack, size, Action.PERFORM);

                stack.shrink(size);

                if (stack.getCount() == 0) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                }
            }
        } else {
            player.inventory.setItemStack(StackUtils.nullToEmpty(network.insertItem(stack, size, Action.PERFORM)));
        }

        player.updateHeldItem();

        network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessGridInsertUsage);
    }

    @Override
    public ItemStack onShiftClick(EntityPlayerMP player, ItemStack stack) {
        return StackUtils.nullToEmpty(onInsert(player, stack, false));
    }

    @Override
    public void onCraftingPreviewRequested(EntityPlayerMP player, UUID id, int quantity, boolean noPreview) {
        if (!network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)) {
            return;
        }

        StackListEntry<ItemStack> stack = network.getItemStorageCache().getCraftablesList().get(id);

        if (stack != null) {
            CraftingManager.CALCULATION_THREAD_POOL.execute(() -> {
                ICraftingTask task = network.getCraftingManager().create(stack.getStack(), quantity);
                if (task == null) {
                    return;
                }

                ICraftingTaskError error = task.calculate();

                if (error == null)
                    network.getCraftingManager().add(task);

                if (error != null) {
                    RS.INSTANCE.network.sendTo(new MessageGridCraftingPreviewResponse(
                                    Collections
                                            .singletonList(new CraftingPreviewElementError(ItemStack.EMPTY)),
                                    task.getId(),
                                    task.getCalculationTime(),
                                    quantity,
                                    false),
                            player);
                } else if (noPreview && !task.hasMissing()) {
                    task.setCanUpdate(true);

                    RS.INSTANCE.network.sendTo(new MessageGridCraftingStartResponse(), player);
                } else {
                    RS.INSTANCE.network
                            .sendTo(new MessageGridCraftingPreviewResponse(task.getPreviewStacks(), task.getId(),
                                    task.getCalculationTime(), quantity, false), player);
                }
            });
        }
    }

    @Override
    public void onCraftingRequested(EntityPlayerMP player, UUID id, int quantity) {
        if (quantity <= 0 || !network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)) {
            return;
        }

        ICraftingTask task = network.getCraftingManager().getTask(id);
        if (task != null)
            task.setCanUpdate(true);
    }

    @Override
    public void onCraftingCancelRequested(EntityPlayerMP player, @Nullable UUID id) {
        if (!network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)) {
            return;
        }

        network.getCraftingManager().cancel(id);

        network.getNetworkItemHandler().drainEnergy(player,
                id == null ? RS.INSTANCE.config.wirelessCraftingMonitorCancelAllUsage :
                        RS.INSTANCE.config.wirelessCraftingMonitorCancelUsage);
    }
}
