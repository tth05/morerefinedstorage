package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileDestructor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerDestructor extends ContainerBase {
    public ContainerDestructor(TileDestructor destructor, EntityPlayer player) {
        super(destructor, player);

        for (int i = 0; i < 4; ++i) {
            addSlotToContainer(new SlotItemHandler(destructor.getNode().getUpgradeHandler(), i, 187, 6 + (i * 18)));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilter(destructor.getNode().getConfig().getItemHandler(), i, 8 + (18 * i), 20).setEnableHandler(() -> destructor.getNode().getConfig().isFilterTypeItem()));
        }

        for (int i = 0; i < 9; ++i) {
            addSlotToContainer(new SlotFilterFluid(destructor.getNode().getConfig().getFluidHandler(), i, 8 + (18 * i), 20).setEnableHandler(() -> destructor.getNode().getConfig().isFilterTypeFluid()));
        }

        addPlayerInventory(8, 55);

        transferManager.addBiTransfer(player.inventory, destructor.getNode().getUpgradeHandler());
        transferManager.addFilterTransfer(player.inventory, destructor.getNode().getConfig().getItemHandler(), destructor.getNode().getConfig().getFluidHandler(), destructor.getNode().getConfig()::getFilterType);
    }
}
