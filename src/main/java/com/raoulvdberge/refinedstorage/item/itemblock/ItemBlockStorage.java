package com.raoulvdberge.refinedstorage.item.itemblock;

import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskSyncData;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.storage.NetworkNodeStorage;
import com.raoulvdberge.refinedstorage.apiimpl.util.OneSixMigrationHelper;
import com.raoulvdberge.refinedstorage.block.BlockStorage;
import com.raoulvdberge.refinedstorage.item.ItemStorageDisk;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class ItemBlockStorage extends ItemBlockBase {
    public ItemBlockStorage(BlockStorage storage) {
        super(storage, true);
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        if (isValid(stack)) {
            UUID id = getId(stack);

            API.instance().getStorageDiskSync().sendRequest(id);

            IStorageDiskSyncData data = API.instance().getStorageDiskSync().getData(id);
            if (data != null) {
                if (data.getCapacity() == -1) {
                    tooltip.add(I18n.format("misc.refinedstorage:storage.stored", API.instance().getQuantityFormatter().format(data.getStored())));
                } else {
                    tooltip.add(I18n.format("misc.refinedstorage:storage.stored_capacity", API.instance().getQuantityFormatter().format(data.getStored()), API.instance().getQuantityFormatter().format(data.getCapacity())));
                }
            }

            if (flag.isAdvanced()) {
                tooltip.add(id.toString());
            }
        }
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack storageStack = player.getHeldItem(hand);

        if (!world.isRemote && player.isSneaking() && storageStack.getMetadata() != ItemStorageDisk.TYPE_CREATIVE) {
            UUID diskId = null;
            IStorageDisk<?> disk = null;

            if (isValid(storageStack)) {
                diskId = getId(storageStack);
                disk = API.instance().getStorageDiskManager(world).get(diskId);
            }

            // Newly created storages won't have a tag yet, so allow invalid disks as well.
            if (disk == null || disk.getStored() == 0) {
                ItemStack storagePart = new ItemStack(RSItems.STORAGE_PART, storageStack.getCount(), storageStack.getMetadata());

                if (!player.inventory.addItemStackToInventory(storagePart.copy())) {
                    InventoryHelper.spawnItemStack(world, player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), storagePart);
                }

                if (disk != null) {
                    API.instance().getStorageDiskManager(world).remove(diskId);
                    API.instance().getStorageDiskManager(world).markForSaving();
                }

                return new ActionResult<>(EnumActionResult.SUCCESS, new ItemStack(RSBlocks.MACHINE_CASING));
            }
        }

        return new ActionResult<>(EnumActionResult.PASS, storageStack);
    }

    @Override
    public int getEntityLifespan(@Nonnull ItemStack stack, @Nonnull World world) {
        return Integer.MAX_VALUE;
    }

    private UUID getId(ItemStack disk) {
        return disk.getTagCompound().getUniqueId(NetworkNodeStorage.NBT_ID);
    }

    private boolean isValid(ItemStack disk) {
        return disk.hasTagCompound() && disk.getTagCompound().hasUniqueId(NetworkNodeStorage.NBT_ID);
    }

    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);

        if (!world.isRemote) {
            OneSixMigrationHelper.migrateItemStorageBlockItem(world, stack);
        }
    }
}
