package com.raoulvdberge.refinedstorage.item.wrench;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class WrenchClickBlockListener {

    @SubscribeEvent
    public void onClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof ItemWrench))
            return;
        event.setUseBlock(Event.Result.DENY);
    }
}
