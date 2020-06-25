package com.raoulvdberge.refinedstorage.network;

import com.google.common.base.Optional;
import com.raoulvdberge.refinedstorage.container.ContainerCraftingMonitor;
import com.raoulvdberge.refinedstorage.item.ItemWirelessCraftingMonitor;
import com.raoulvdberge.refinedstorage.tile.craftingmonitor.WirelessCraftingMonitor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

public class MessageWirelessCraftingMonitorSettings extends MessageHandlerPlayerToServer<MessageWirelessCraftingMonitorSettings> implements IMessage {
    private Optional<UUID> tabSelected = Optional.absent();
    private int tabPage;

    public MessageWirelessCraftingMonitorSettings() {
    }

    public MessageWirelessCraftingMonitorSettings(Optional<UUID> tabSelected, int tabPage) {
        this.tabSelected = tabSelected;
        this.tabPage = tabPage;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readBoolean()) {
            tabSelected = Optional.of(new UUID(buf.readLong(), buf.readLong()));
        }

        tabPage = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(tabSelected.isPresent());
        if(tabSelected.isPresent()) {
            buf.writeLong(tabSelected.get().getMostSignificantBits());
            buf.writeLong(tabSelected.get().getLeastSignificantBits());
        }

        buf.writeInt(tabPage);
    }

    @Override
    public void handle(MessageWirelessCraftingMonitorSettings message, EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerCraftingMonitor) {
            ItemStack stack = ((WirelessCraftingMonitor) ((ContainerCraftingMonitor) player.openContainer).getCraftingMonitor()).getStack();

            ItemWirelessCraftingMonitor.setTabPage(stack, message.tabPage);
            ItemWirelessCraftingMonitor.setTabSelected(stack, message.tabSelected);
        }
    }
}
