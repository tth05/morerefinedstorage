package com.raoulvdberge.refinedstorage.container.slot.grid;

import com.raoulvdberge.refinedstorage.api.network.grid.IGrid;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class SlotGridCraftingResult extends SlotCrafting {
    private final ContainerGrid container;
    private final IGrid grid;

    public SlotGridCraftingResult(ContainerGrid container, EntityPlayer player, IGrid grid, int inventoryIndex, int x, int y) {
        //noinspection ConstantConditions
        super(player, grid.getCraftingMatrix(), grid.getCraftingResult(), inventoryIndex, x, y);

        this.container = container;
        this.grid = grid;
    }

    @Override
    @Nonnull
    public ItemStack onTake(EntityPlayer player, @Nonnull ItemStack stack) {
        onCrafting(stack);

        if (!player.getEntityWorld().isRemote) {
            grid.onCrafted(player);
            container.detectAndSendChanges();
        }

        return ItemStack.EMPTY;
    }
}
