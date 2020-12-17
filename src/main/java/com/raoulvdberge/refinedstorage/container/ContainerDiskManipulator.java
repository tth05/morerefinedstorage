package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileDiskManipulator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerDiskManipulator extends ContainerBase {
    public ContainerDiskManipulator(TileDiskManipulator diskManipulator, EntityPlayer player) {
        super(diskManipulator, player);

        for (int i = 0; i < 4; ++i) {
            addSlotToContainer(new SlotItemHandler(diskManipulator.getNode().getUpgradeHandler(), i, 187, 6 + (i * 18)));
        }

        for (int i = 0; i < 3; ++i) {
            addSlotToContainer(new SlotItemHandler(diskManipulator.getNode().getInputDisks(), i, 44, 57 + (i * 18)));
        }

        for (int i = 0; i < 3; ++i) {
            addSlotToContainer(new SlotItemHandler(diskManipulator.getNode().getOutputDisks(), i, 116, 57 + (i * 18)));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilter(diskManipulator.getNode().getConfig().getItemFilters(), i, 8 + (18 * i), 20).setEnableHandler(() -> diskManipulator.getNode().getConfig().isFilterTypeItem()));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilterFluid(diskManipulator.getNode().getConfig().getFluidFilters(), i, 8 + (18 * i), 20).setEnableHandler(() -> diskManipulator.getNode().getConfig().isFilterTypeFluid()));
        }

        addPlayerInventory(8, 129);

        transferManager.addBiTransfer(player.inventory, diskManipulator.getNode().getUpgradeHandler());
        transferManager.addBiTransfer(player.inventory, diskManipulator.getNode().getInputDisks());
        transferManager.addTransfer(diskManipulator.getNode().getOutputDisks(), player.inventory);
        transferManager.addFilterTransfer(player.inventory, diskManipulator.getNode().getConfig().getItemFilters(), diskManipulator.getNode().getConfig().getFluidFilters(), diskManipulator.getNode().getConfig()::getFilterType);
    }
}
