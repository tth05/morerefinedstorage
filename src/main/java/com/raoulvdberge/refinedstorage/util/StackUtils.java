package com.raoulvdberge.refinedstorage.util;

import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskProvider;
import com.raoulvdberge.refinedstorage.api.storage.tracker.StorageTrackerEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackFluid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Function;

public final class StackUtils {
    public static final ItemStack EMPTY_BUCKET = new ItemStack(Items.BUCKET);

    private static final String NBT_INVENTORY = "Inventory_%d";
    private static final String NBT_SLOT = "Slot";

    public static void writeItemStack(ByteBuf buf, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeInt(Item.getIdFromItem(stack.getItem()));
            buf.writeInt(stack.getCount());
            buf.writeShort(stack.getItemDamage());
            ByteBufUtils.writeTag(buf, stack.getItem().getNBTShareTag(stack));
        }
    }

    public static ItemStack readItemStack(ByteBuf buf) {
        if (!buf.readBoolean()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack stack = new ItemStack(Item.getItemById(buf.readInt()), buf.readInt(), buf.readShort());
            stack.setTagCompound(ByteBufUtils.readTag(buf));
            return stack;
        }
    }

    public static void writeItemGridStack(ByteBuf buf, ItemStack stack, UUID id, @Nullable UUID otherId, boolean craftable, @Nullable StorageTrackerEntry entry) {
        writeItemStack(buf, stack);

        buf.writeBoolean(craftable);
        ByteBufUtils.writeUTF8String(buf, id.toString());

        buf.writeBoolean(otherId != null);
        if (otherId != null) {
            ByteBufUtils.writeUTF8String(buf, otherId.toString());
        }

        if (entry == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);

            buf.writeLong(entry.getTime());
            ByteBufUtils.writeUTF8String(buf, entry.getName());
        }
    }

    public static GridStackItem readItemGridStack(ByteBuf buf) {
        ItemStack stack = readItemStack(buf);

        boolean craftable = buf.readBoolean();
        UUID id = UUID.fromString(ByteBufUtils.readUTF8String(buf));

        UUID otherId = null;
        if (buf.readBoolean()) {
            otherId = UUID.fromString(ByteBufUtils.readUTF8String(buf));
        }

        StorageTrackerEntry entry = null;
        if (buf.readBoolean()) {
            entry = new StorageTrackerEntry(buf.readLong(), ByteBufUtils.readUTF8String(buf));
        }

        return new GridStackItem(id, otherId, stack, craftable, entry);
    }

    public static void writeFluidGridStack(ByteBuf buf, FluidStack stack, UUID id, @Nullable UUID otherId, boolean craftable, @Nullable StorageTrackerEntry entry) {
        ByteBufUtils.writeTag(buf, stack.writeToNBT(new NBTTagCompound()));

        buf.writeBoolean(craftable);
        ByteBufUtils.writeUTF8String(buf, id.toString());

        buf.writeBoolean(otherId != null);
        if (otherId != null) {
            ByteBufUtils.writeUTF8String(buf, otherId.toString());
        }

        if (entry == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);

            buf.writeLong(entry.getTime());
            ByteBufUtils.writeUTF8String(buf, entry.getName());
        }
    }

    public static GridStackFluid readFluidGridStack(ByteBuf buf) {
        FluidStack stack = FluidStack.loadFluidStackFromNBT(ByteBufUtils.readTag(buf));
        boolean craftable = buf.readBoolean();
        UUID id = UUID.fromString(ByteBufUtils.readUTF8String(buf));

        UUID otherId = null;
        if (buf.readBoolean()) {
            otherId = UUID.fromString(ByteBufUtils.readUTF8String(buf));
        }

        StorageTrackerEntry entry = null;
        if (buf.readBoolean()) {
            entry = new StorageTrackerEntry(buf.readLong(), ByteBufUtils.readUTF8String(buf));
        }

        return new GridStackFluid(id, otherId, stack, entry, craftable);
    }

    public static ItemStack nullToEmpty(@Nullable ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Nullable
    public static ItemStack emptyToNull(@Nonnull ItemStack stack) {
        return stack.isEmpty() ? null : stack;
    }

    @SuppressWarnings("unchecked")
    public static void createStorages(World world, ItemStack diskStack, int slot, IStorageDisk<ItemStack>[] itemDisks, IStorageDisk<FluidStack>[] fluidDisks, Function<IStorageDisk<ItemStack>, IStorageDisk> itemDiskWrapper, Function<IStorageDisk<FluidStack>, IStorageDisk> fluidDiskWrapper) {
        if (diskStack.isEmpty()) {
            itemDisks[slot] = null;
            fluidDisks[slot] = null;
        } else {
            IStorageDisk disk = API.instance().getStorageDiskManager(world).getByStack(diskStack);

            if (disk != null) {
                switch (((IStorageDiskProvider) diskStack.getItem()).getType()) {
                    case ITEM: {
                        itemDisks[slot] = itemDiskWrapper.apply(disk);
                        break;
                    }
                    case FLUID: {
                        fluidDisks[slot] = fluidDiskWrapper.apply(disk);
                        break;
                    }
                }
            } else {
                itemDisks[slot] = null;
                fluidDisks[slot] = null;
            }
        }
    }

    public static void writeItems(IItemHandler handler, int id, NBTTagCompound tag, Function<ItemStack, NBTTagCompound> serializer) {
        NBTTagList tagList = new NBTTagList();

        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                NBTTagCompound stackTag = serializer.apply(handler.getStackInSlot(i));

                stackTag.setInteger(NBT_SLOT, i);

                tagList.appendTag(stackTag);
            }
        }

        tag.setTag(String.format(NBT_INVENTORY, id), tagList);
    }

    public static void writeItems(IItemHandler handler, int id, NBTTagCompound tag) {
        writeItems(handler, id, tag, stack -> stack.writeToNBT(new NBTTagCompound()));
    }

    public static void readItems(IItemHandlerModifiable handler, int id, NBTTagCompound tag, Function<NBTTagCompound, ItemStack> deserializer) {
        String name = String.format(NBT_INVENTORY, id);

        if (tag.hasKey(name)) {
            NBTTagList tagList = tag.getTagList(name, Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < tagList.tagCount(); i++) {
                int slot = tagList.getCompoundTagAt(i).getInteger(NBT_SLOT);

                if (slot >= 0 && slot < handler.getSlots()) {
                    handler.setStackInSlot(slot, deserializer.apply(tagList.getCompoundTagAt(i)));
                }
            }
        }
    }

    public static void readItems(IItemHandlerModifiable handler, int id, NBTTagCompound tag) {
        readItems(handler, id, tag, ItemStack::new);
    }

    public static void writeItems(IInventory inventory, int id, NBTTagCompound tag) {
        NBTTagList tagList = new NBTTagList();

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                NBTTagCompound stackTag = new NBTTagCompound();

                stackTag.setInteger(NBT_SLOT, i);

                inventory.getStackInSlot(i).writeToNBT(stackTag);

                tagList.appendTag(stackTag);
            }
        }

        tag.setTag(String.format(NBT_INVENTORY, id), tagList);
    }

    public static void readItems(IInventory inventory, int id, NBTTagCompound tag) {
        String name = String.format(NBT_INVENTORY, id);

        if (tag.hasKey(name)) {
            NBTTagList tagList = tag.getTagList(name, Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < tagList.tagCount(); i++) {
                int slot = tagList.getCompoundTagAt(i).getInteger(NBT_SLOT);

                ItemStack stack = new ItemStack(tagList.getCompoundTagAt(i));

                if (!stack.isEmpty()) {
                    inventory.setInventorySlotContents(slot, stack);
                }
            }
        }
    }

    public static boolean hasFluidBucket(FluidStack stack) {
        return stack.getFluid() == FluidRegistry.WATER || stack.getFluid() == FluidRegistry.LAVA || stack.getFluid().getName().equals("milk") || FluidRegistry.getBucketFluids().contains(stack.getFluid());
    }

    public static FluidStack copy(FluidStack stack, int size) {
        FluidStack copy = stack.copy();
        copy.amount = size;
        return copy;
    }

    public static FluidStack copy(@Nullable FluidStack stack) {
        return stack == null ? null : stack.copy();
    }

    public static Pair<ItemStack, FluidStack> getFluid(ItemStack stack, boolean simulate) {
        // We won't have the capability on stacks with size bigger than 1.
        if (stack.getCount() > 1) {
            stack = ItemHandlerHelper.copyStackWithSize(stack, 1);
        }

        if (stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fluidHandler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);

            FluidStack result = fluidHandler.drain(Fluid.BUCKET_VOLUME, !simulate);

            return Pair.of(fluidHandler.getContainer(), result);
        }

        return Pair.of(null, null);
    }

    private static final String NBT_ITEM_TYPE = "Type";
    private static final String NBT_ITEM_QUANTITY = "Quantity";
    private static final String NBT_ITEM_DAMAGE = "Damage";
    private static final String NBT_ITEM_NBT = "NBT";
    private static final String NBT_ITEM_CAPS = "Caps";

    public static NBTTagCompound serializeStackToNbt(@Nonnull ItemStack stack) {
        NBTTagCompound dummy = new NBTTagCompound();

        NBTTagCompound itemTag = new NBTTagCompound();

        itemTag.setInteger(NBT_ITEM_TYPE, Item.getIdFromItem(stack.getItem()));
        itemTag.setInteger(NBT_ITEM_QUANTITY, stack.getCount());
        itemTag.setInteger(NBT_ITEM_DAMAGE, stack.getItemDamage());

        if (stack.hasTagCompound()) {
            itemTag.setTag(NBT_ITEM_NBT, stack.getTagCompound());
        }

        stack.writeToNBT(dummy);

        if (dummy.hasKey("ForgeCaps")) {
            itemTag.setTag(NBT_ITEM_CAPS, dummy.getTag("ForgeCaps"));
        }

        dummy.removeTag("ForgeCaps");

        return itemTag;
    }

    @Nonnull
    public static ItemStack deserializeStackFromNbt(NBTTagCompound tag) {
        ItemStack stack = new ItemStack(
            Item.getItemById(tag.getInteger(NBT_ITEM_TYPE)),
            tag.getInteger(NBT_ITEM_QUANTITY),
            tag.getInteger(NBT_ITEM_DAMAGE),
            tag.hasKey(NBT_ITEM_CAPS) ? tag.getCompoundTag(NBT_ITEM_CAPS) : null
        );

        stack.setTagCompound(tag.hasKey(NBT_ITEM_NBT) ? tag.getCompoundTag(NBT_ITEM_NBT) : null);

        return stack;
    }
}
