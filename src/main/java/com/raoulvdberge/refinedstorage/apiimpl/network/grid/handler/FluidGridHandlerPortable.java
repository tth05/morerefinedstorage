package com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IFluidGridHandler;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.tile.grid.portable.IPortableGrid;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.UUID;

public class FluidGridHandlerPortable implements IFluidGridHandler {
    private final IPortableGrid portableGrid;

    public FluidGridHandlerPortable(IPortableGrid portableGrid) {
        this.portableGrid = portableGrid;
    }

    @Override
    public void onExtract(EntityPlayerMP player, UUID id, boolean shift) {
        StackListEntry<FluidStack> stack = portableGrid.getFluidCache().getList().get(id);

        if (stack == null || stack.getCount() < Fluid.BUCKET_VOLUME ||
                (portableGrid instanceof IGrid && !((IGrid) portableGrid).isActive())) {
            return;
        }

        if (StackUtils.hasFluidBucket(stack.getStack())) {
            ItemStack bucket = null;

            for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
                ItemStack slot = player.inventory.getStackInSlot(i);

                if (API.instance().getComparer().isEqualNoQuantity(StackUtils.EMPTY_BUCKET, slot)) {
                    bucket = StackUtils.EMPTY_BUCKET.copy();

                    player.inventory.decrStackSize(i, 1);

                    break;
                }
            }

            if (bucket != null) {
                IFluidHandlerItem fluidHandler = bucket.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);

                portableGrid.getFluidStorageTracker().changed(player, stack.getStack().copy());

                StackListResult<FluidStack> entry = portableGrid.getFluidStorage().extract(stack.getStack(), Fluid.BUCKET_VOLUME, IComparer.COMPARE_NBT, Action.PERFORM);
                if(entry != null) {
                    fluidHandler.fill(entry.getFixedStack(), true);
                }

                if (shift) {
                    if (!player.inventory.addItemStackToInventory(fluidHandler.getContainer().copy())) {
                        InventoryHelper.spawnItemStack(player.getEntityWorld(), player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), fluidHandler.getContainer());
                    }
                } else {
                    player.inventory.setItemStack(fluidHandler.getContainer());
                    player.updateHeldItem();
                }

                portableGrid.drainEnergy(RS.INSTANCE.config.portableGridExtractUsage);
            }
        }
    }

    @Nullable
    @Override
    public ItemStack onInsert(EntityPlayerMP player, ItemStack container) {
        if((portableGrid instanceof IGrid && !((IGrid) portableGrid).isActive()))
            return container;

        Pair<ItemStack, FluidStack> result = StackUtils.getFluid(container, true);

        if (result.getValue() != null && portableGrid.getFluidStorage().insert(result.getValue(), result.getValue().amount, Action.SIMULATE) == null) {
            portableGrid.getFluidStorageTracker().changed(player, result.getValue().copy());

            result = StackUtils.getFluid(container, false);

            portableGrid.getFluidStorage().insert(result.getValue(), result.getValue().amount, Action.PERFORM);

            portableGrid.drainEnergy(RS.INSTANCE.config.portableGridInsertUsage);

            return result.getLeft();
        }

        return container;
    }

    @Override
    public void onInsertHeldContainer(EntityPlayerMP player) {
        player.inventory.setItemStack(StackUtils.nullToEmpty(onInsert(player, player.inventory.getItemStack())));
        player.updateHeldItem();
    }

    @Override
    public ItemStack onShiftClick(EntityPlayerMP player, ItemStack container) {
        return StackUtils.nullToEmpty(onInsert(player, container));
    }

    @Override
    public void onCraftingPreviewRequested(EntityPlayerMP player, UUID id, int quantity, boolean noPreview) {
        // NO OP
    }

    @Override
    public void onCraftingRequested(EntityPlayerMP player, UUID id, int quantity) {
        // NO OP
    }
}
