package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileImporter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerImporter extends ContainerBase {
    public ContainerImporter(TileImporter importer, EntityPlayer player) {
        super(importer, player);

        for (int i = 0; i < 4; ++i) {
            addSlotToContainer(new SlotItemHandler(importer.getNode().getUpgradeHandler(), i, 187, 6 + (i * 18)));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilter(importer.getNode().getConfig().getItemFilters(), i, 8 + (18 * i), 20).setEnableHandler(() -> importer.getNode().getConfig().isFilterTypeItem()));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilterFluid(importer.getNode().getConfig().getFluidFilters(), i, 8 + (18 * i), 20).setEnableHandler(() -> importer.getNode().getConfig().isFilterTypeFluid()));
        }

        addPlayerInventory(8, 55);

        transferManager.addBiTransfer(player.inventory, importer.getNode().getUpgradeHandler());
        transferManager.addFilterTransfer(player.inventory, importer.getNode().getConfig().getItemFilters(), importer.getNode().getConfig().getFluidFilters(), importer.getNode().getConfig()::getFilterType);
    }
}
