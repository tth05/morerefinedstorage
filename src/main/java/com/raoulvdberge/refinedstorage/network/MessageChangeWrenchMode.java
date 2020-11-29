package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.item.wrench.ItemWrench;
import com.raoulvdberge.refinedstorage.item.wrench.WrenchMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageChangeWrenchMode extends MessageHandlerPlayerToServer<MessageChangeWrenchMode> implements IMessage {
    private int mode;
    private int slot;

    public MessageChangeWrenchMode() {
    }

    public MessageChangeWrenchMode(int mode, int slot) {
        this.mode = mode;
        this.slot = slot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mode = buf.readInt();
        this.slot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.mode);
        buf.writeInt(this.slot);
    }

    @Override
    public void handle(MessageChangeWrenchMode message, EntityPlayerMP player) {
        ItemStack stack = player.inventory.getStackInSlot(message.slot);
        ItemWrench.addDefaultMode(stack);

        NBTTagCompound tagCompound = stack.getTagCompound();

        WrenchMode newMode = WrenchMode.values()[message.mode];
        tagCompound.setString("mode", newMode.name());
    }
}
