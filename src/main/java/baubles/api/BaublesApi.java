package baubles.api;

import baubles.api.cap.BaublesCapabilities;
import baubles.api.cap.IBaublesItemHandler;
import baubles.api.inv.BaublesInventoryWrapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;

import javax.annotation.Nullable;

/**
 * @author Azanor
 */
public class BaublesApi {
    /**
     * Retrieves the baubles inventory capability handler for the supplied player
     */
    @Nullable
    public static IBaublesItemHandler getBaublesHandler(EntityPlayer player) {
        IBaublesItemHandler handler = player.getCapability(BaublesCapabilities.CAPABILITY_BAUBLES, null);
        if (handler == null)
            return null;
        handler.setPlayer(player);
        return handler;
    }

    /**
     * Retrieves the baubles capability handler wrapped as a IInventory for the supplied player
     * @deprecated use {@link #getBaublesHandler(EntityPlayer)}
     */
    @Deprecated
    public static IInventory getBaubles(EntityPlayer player)
    {
        IBaublesItemHandler handler = player.getCapability(BaublesCapabilities.CAPABILITY_BAUBLES, null);
        handler.setPlayer(player);
        return new BaublesInventoryWrapper(handler, player);
    }

    /**
     * Returns if the passed in item is equipped in a bauble slot. Will return the first slot found
     *
     * @return -1 if not found and slot number if it is found
     */
    public static int isBaubleEquipped(EntityPlayer player, Item bauble) {
        IBaublesItemHandler handler = getBaublesHandler(player);
        if (handler == null)
            return -1;

        for (int a = 0; a < handler.getSlots(); a++) {
            if (!handler.getStackInSlot(a).isEmpty() && handler.getStackInSlot(a).getItem() == bauble)
                return a;
        }
        return -1;
    }
}
