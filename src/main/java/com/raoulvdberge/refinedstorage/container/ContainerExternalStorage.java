package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileExternalStorage;
import net.minecraft.entity.player.EntityPlayer;

public class ContainerExternalStorage extends ContainerBase {
    public ContainerExternalStorage(TileExternalStorage externalStorage, EntityPlayer player) {
        super(externalStorage, player);

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilter(externalStorage.getNode().getConfig().getItemFilters(), i, 8 + (18 * i), 20).setEnableHandler(() -> externalStorage.getNode().getConfig().isFilterTypeItem()));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilterFluid(externalStorage.getNode().getConfig().getFluidFilters(), i, 8 + (18 * i), 20).setEnableHandler(() -> externalStorage.getNode().getConfig().isFilterTypeFluid()));
        }

        addPlayerInventory(8, 141);

        transferManager.addFilterTransfer(player.inventory, externalStorage.getNode().getConfig().getItemFilters(), externalStorage.getNode().getConfig().getFluidFilters(), externalStorage.getNode().getConfig()::getFilterType);
    }
}
