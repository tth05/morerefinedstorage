package com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSTriggers;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementError;
import com.raoulvdberge.refinedstorage.network.MessageGridCraftingPreviewResponse;
import com.raoulvdberge.refinedstorage.network.MessageGridCraftingStartResponse;
import com.raoulvdberge.refinedstorage.util.StackUtils;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class ItemGridHandler implements IItemGridHandler {
    private final INetwork network;

    public ItemGridHandler(INetwork network) {
        this.network = network;
    }

    public void onExtract(EntityPlayerMP player, ItemStack stack, int preferredSlot, int flags) {
        StackListEntry<ItemStack> entry = this.network.getItemStorageCache().getList().getEntry(stack, 3);
        if (entry != null) {
            this.onExtract(player, entry.getId(), preferredSlot, flags);
        }

    }

    public void onExtract(EntityPlayerMP player, UUID id, int preferredSlot, int flags) {
        StackListEntry<ItemStack> entry = this.network.getItemStorageCache().getList().get(id);
        if (entry != null && this.network.getSecurityManager().hasPermission(Permission.EXTRACT, player)) {
            long itemSize = entry.getCount();
            int maxItemSize = entry.getStack().getItem().getItemStackLimit(entry.getStack().copy());
            boolean single = (flags & 2) == 2;
            ItemStack held = player.inventory.getItemStack();
            if (single) {
                if (!held.isEmpty() && (!API.instance().getComparer().isEqualNoQuantity(entry.getStack(), held) || held.getCount() + 1 > held.getMaxStackSize())) {
                    return;
                }
            } else if (!player.inventory.getItemStack().isEmpty()) {
                return;
            }

            long size = 64L;
            if ((flags & 1) == 1 && itemSize > 1L) {
                size = itemSize / 2L;
                if (size > (long) (maxItemSize / 2) && maxItemSize != 1) {
                    size = maxItemSize / 2;
                }
            } else if (single) {
                size = 1L;
            }

            size = Math.min(size, maxItemSize);
            this.network.getItemStorageTracker().changed(player, entry.getStack().copy());
            StackListResult<ItemStack> took = this.network.extractItem(entry.getStack(), size, Action.SIMULATE);
            if (took != null) {
                if ((flags & 4) == 4) {
                    IItemHandler playerInventory = player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
                    if (playerInventory != null) {
                        ItemStack remainder;
                        if (preferredSlot != -1) {
                            remainder = playerInventory.insertItem(preferredSlot, took.getFixedStack(), true);
                            if ((long) remainder.getCount() != took.getCount()) {
                                StackListResult<ItemStack> inserted = this.network.extractItem(entry.getStack(), size - (long) remainder.getCount(), Action.PERFORM);
                                playerInventory.insertItem(preferredSlot, StackListResult.nullToEmpty(inserted), false);
                                took.setCount(remainder.getCount());
                            }
                        }

                        remainder = ItemHandlerHelper.insertItemStacked(playerInventory, took.getFixedStack(), false);
                        if (took.getCount() - (long) remainder.getCount() > 0L) {
                            this.network.extractItem(entry.getStack(), took.getCount() - (long) remainder.getCount(), Action.PERFORM);
                        }
                    }
                } else {
                    took = this.network.extractItem(entry.getStack(), size, Action.PERFORM);
                    if (took != null) {
                        if (single && !held.isEmpty()) {
                            held.grow(1);
                        } else {
                            player.inventory.setItemStack(took.getFixedStack());
                        }

                        player.updateHeldItem();
                    }
                }

                this.network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessGridExtractUsage);
            }
        }
    }

    public ItemStack onInsert(EntityPlayerMP player, ItemStack stack, boolean single) {
        if (!this.network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            return stack;
        } else {
            this.grantAdvancement(player);
            this.network.getItemStorageTracker().changed(player, stack.copy());
            ItemStack remainder;
            if (single) {
                if (this.network.insertItem(stack, 1, Action.SIMULATE) == null) {
                    this.network.insertItem(stack, 1, Action.PERFORM);
                    stack.shrink(1);
                }

                remainder = stack;
            } else {
                remainder = this.network.insertItem(stack, stack.getCount(), Action.PERFORM);
            }

            this.network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessGridInsertUsage);
            return remainder;
        }
    }

    public void onInsertHeldItem(EntityPlayerMP player, boolean single) {
        if (!player.inventory.getItemStack().isEmpty() && this.network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            this.grantAdvancement(player);
            ItemStack stack = player.inventory.getItemStack();
            int size = single ? 1 : stack.getCount();
            this.network.getItemStorageTracker().changed(player, stack.copy());
            if (single) {
                if (this.network.insertItem(stack, size, Action.SIMULATE) == null) {
                    this.network.insertItem(stack, size, Action.PERFORM);
                    stack.shrink(size);
                    if (stack.getCount() == 0) {
                        player.inventory.setItemStack(ItemStack.EMPTY);
                    }
                }
            } else {
                player.inventory.setItemStack(StackUtils.nullToEmpty(this.network.insertItem(stack, size, Action.PERFORM)));
            }

            player.updateHeldItem();
            this.network.getNetworkItemHandler().drainEnergy(player, RS.INSTANCE.config.wirelessGridInsertUsage);
        }
    }

    private void grantAdvancement(EntityPlayerMP player) {
        if (this.network.getItemStorageCache().getList().getStored() > 100000000000L) {
            RSTriggers.ONE_HUNDRED_BILLION_ITEMS_TRIGGER.trigger(player);
        }

    }

    public ItemStack onShiftClick(EntityPlayerMP player, ItemStack stack) {
        return StackUtils.nullToEmpty(this.onInsert(player, stack, false));
    }

    public void onCraftingPreviewRequested(EntityPlayerMP player, UUID id, int quantity, boolean noPreview) {
        if (this.network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)) {
            StackListEntry<ItemStack> stack = this.network.getItemStorageCache().getCraftablesList().get(id);
            if (stack != null) {
                ICraftingTask task = this.network.getCraftingManager().create(stack.getStack(), quantity);
                if (task == null) {
                    return;
                }

                FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                    ICraftingTaskError error = task.calculate();
                    if (error == null && !task.hasMissing()) {
                        this.network.getCraftingManager().add(task);
                    }

                    if (error != null) {
                        RS.INSTANCE.network.sendTo(new MessageGridCraftingPreviewResponse(Collections.singletonList(new CraftingPreviewElementError()), task.getId(), task.getCalculationTime(), quantity, false), player);
                    } else if (noPreview && !task.hasMissing()) {
                        task.setCanUpdate(true);
                        RS.INSTANCE.network.sendTo(new MessageGridCraftingStartResponse(), player);
                    } else {
                        RS.INSTANCE.network.sendTo(new MessageGridCraftingPreviewResponse(task.getPreviewStacks(), task.getId(), task.getCalculationTime(), quantity, false), player);
                    }

                });
            }

        }
    }

    public void onCraftingRequested(EntityPlayerMP player, UUID id, int quantity) {
        if (quantity > 0 && this.network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)) {
            ICraftingTask task = this.network.getCraftingManager().getTask(id);
            if (task != null) {
                task.setCanUpdate(true);
            }

        }
    }

    public void onCraftingCancelRequested(EntityPlayerMP player, @Nullable UUID id) {
        if (this.network.getSecurityManager().hasPermission(Permission.AUTOCRAFTING, player)) {
            this.network.getCraftingManager().cancel(id);
            this.network.getNetworkItemHandler().drainEnergy(player, id == null ? RS.INSTANCE.config.wirelessCraftingMonitorCancelAllUsage : RS.INSTANCE.config.wirelessCraftingMonitorCancelUsage);
        }
    }
}
