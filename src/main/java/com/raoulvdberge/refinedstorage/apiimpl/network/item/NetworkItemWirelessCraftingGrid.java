package com.raoulvdberge.refinedstorage.apiimpl.network.item;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.item.INetworkItemHandler;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.item.ItemEnergyItem;
import com.raoulvdberge.refinedstorage.tile.grid.WirelessCraftingGrid;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class NetworkItemWirelessCraftingGrid extends NetworkItemWirelessGrid {

    private final INetworkItemHandler handler;
    private final ItemStack stack;
    private final EntityPlayer player;
    private final int slotId;

    public NetworkItemWirelessCraftingGrid(final INetworkItemHandler handler, final EntityPlayer player,
                                           final ItemStack stack, final int slotId) {
        super(handler, player, stack, slotId);
        this.handler = handler;
        this.player = player;
        this.stack = stack;
        this.slotId = slotId;
    }

    public boolean onOpen(final INetwork network) {
        if (RS.INSTANCE.config.wirelessCraftingGridUsesEnergy && this.stack.getItemDamage() != ItemEnergyItem.TYPE_CREATIVE && this.stack.getCapability(CapabilityEnergy.ENERGY, null).getEnergyStored() <= RS.INSTANCE.config.wirelessCraftingGridOpenUsage) {
            return false;
        }
        if (!network.getSecurityManager().hasPermission(Permission.MODIFY, this.player)) {
            WorldUtils.sendNoPermissionMessage(this.player);
            return false;
        }
        API.instance().getGridManager().openGrid(WirelessCraftingGrid.ID, (EntityPlayerMP) this.player, this.stack, this.slotId);
        this.drainEnergy(RS.INSTANCE.config.wirelessCraftingGridOpenUsage);
        return true;
    }

    public void drainEnergy(final int energy) {
        if (RS.INSTANCE.config.wirelessCraftingGridUsesEnergy && this.stack.getItemDamage() != ItemEnergyItem.TYPE_CREATIVE) {
            IEnergyStorage energyStorage = this.stack.getCapability(CapabilityEnergy.ENERGY, null);
            energyStorage.extractEnergy(energy, false);
            if (energyStorage.getEnergyStored() <= 0) {
                this.handler.close(this.getPlayer());
                this.getPlayer().closeScreen();
            }
        }
    }
}
