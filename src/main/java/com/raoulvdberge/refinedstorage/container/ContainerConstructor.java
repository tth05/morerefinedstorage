package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileConstructor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerConstructor extends ContainerBase {
    public ContainerConstructor(TileConstructor constructor, EntityPlayer player) {
        super(constructor, player);

        for (int i = 0; i < 4; ++i) {
            addSlotToContainer(new SlotItemHandler(constructor.getNode().getUpgradeHandler(), i, 187, 6 + (i * 18)));
        }

        addSlotToContainer(new SlotFilter(constructor.getNode().getConfig().getItemFilters(), 0, 80, 20).setEnableHandler(() -> constructor.getNode().getConfig().isFilterTypeItem()));
        addSlotToContainer(new SlotFilterFluid(constructor.getNode().getConfig().getFluidFilters(), 0, 80, 20, 0).setEnableHandler(() -> constructor.getNode().getConfig().isFilterTypeFluid()));

        addPlayerInventory(8, 55);

        transferManager.addBiTransfer(player.inventory, constructor.getNode().getUpgradeHandler());
        transferManager.addFilterTransfer(player.inventory, constructor.getNode().getConfig().getItemFilters(), constructor.getNode().getConfig().getFluidFilters(), constructor.getNode().getConfig()::getFilterType);
    }
}
