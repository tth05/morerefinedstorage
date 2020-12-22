package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileFluidStorage;
import net.minecraft.entity.player.EntityPlayer;

public class ContainerFluidStorage extends ContainerBase {
    public ContainerFluidStorage(TileFluidStorage fluidStorage, EntityPlayer player) {
        super(fluidStorage, player);

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilterFluid(fluidStorage.getNode().getConfig().getFluidHandler(), i, 8 + (18 * i), 20));
        }

        addPlayerInventory(8, 141);

        transferManager.addFluidFilterTransfer(player.inventory, fluidStorage.getNode().getConfig().getFluidHandler());
    }
}
