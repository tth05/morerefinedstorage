package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.RedstoneMode;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class NetworkNodeStorageMonitor extends NetworkNode implements IRSFilterConfigProvider {
    public static final int DEPOSIT_ALL_MAX_DELAY = 500;

    public static final String ID = "storage_monitor";

    private final Map<String, Pair<ItemStack, Long>> deposits = new HashMap<>();

    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterModeWhitelist()
            .allowedFilterTypeItems()
            .filterSizeOne()
            .compareDamageAndNbt()
            .onItemFilterChanged((slot) -> WorldUtils.updateBlock(world, pos)).build();

    private long oldAmount = -1;

    public NetworkNodeStorageMonitor(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public void updateNetworkNode() {
        super.updateNetworkNode();

        long newAmount = getAmount();

        if (oldAmount == -1) {
            oldAmount = newAmount;
        } else if (oldAmount != newAmount) {
            oldAmount = newAmount;

            WorldUtils.updateBlock(world, pos);
        }
    }

    public boolean depositAll(EntityPlayer player) {
        if (network == null) {
            return false;
        }

        if (!network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            return false;
        }

        Pair<ItemStack, Long> deposit = deposits.get(player.getGameProfile().getName());

        if (deposit == null) {
            return false;
        }

        ItemStack inserted = deposit.getKey();
        long insertedAt = deposit.getValue();

        if (MinecraftServer.getCurrentTimeMillis() - insertedAt < DEPOSIT_ALL_MAX_DELAY) {
            for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
                ItemStack toInsert = player.inventory.getStackInSlot(i);

                if (API.instance().getComparer().isEqual(inserted, toInsert, this.config.getCompare())) {
                    player.inventory.setInventorySlotContents(i, StackUtils.nullToEmpty(network.insertItemTracked(toInsert, toInsert.getCount())));
                }
            }
        }

        return true;
    }

    public boolean deposit(EntityPlayer player, ItemStack toInsert) {
        if (network == null) {
            return false;
        }

        if (!network.getSecurityManager().hasPermission(Permission.INSERT, player)) {
            return false;
        }

        ItemStack filter = this.config.getItemHandler().getStackInSlot(0);

        if (!filter.isEmpty() && API.instance().getComparer().isEqual(filter, toInsert, this.config.getCompare())) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, StackUtils.nullToEmpty(network.insertItemTracked(toInsert, toInsert.getCount())));

            deposits.put(player.getGameProfile().getName(), Pair.of(toInsert, MinecraftServer.getCurrentTimeMillis()));
        }

        return true;
    }

    public void extract(EntityPlayer player, EnumFacing side) {
        if (network == null || getDirection() != side) {
            return;
        }

        if (!network.getSecurityManager().hasPermission(Permission.EXTRACT, player)) {
            return;
        }

        ItemStack filter = this.config.getItemHandler().getStackInSlot(0);

        int toExtract = player.isSneaking() ? 1 : 64;

        if (!filter.isEmpty()) {
            ItemStack result = network.extractItem(filter, toExtract, this.config.getCompare(), Action.PERFORM);

            if (!result.isEmpty() && !player.inventory.addItemStackToInventory(result.copy())) {
                InventoryHelper.spawnItemStack(world, player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), result);
            }
        }
    }

    @Override
    public int getEnergyUsage() {
        return 0;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);
        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);
    }

    public long getAmount() {
        if (network == null) {
            return 0;
        }

        ItemStack toCheck = this.config.getItemHandler().getStackInSlot(0);

        if (toCheck.isEmpty()) {
            return 0;
        }

        StackListEntry<ItemStack> stored = network.getItemStorageCache().getList().getEntry(toCheck, this.config.getCompare());

        return stored != null ? stored.getCount() : 0;
    }

    @Override
    public boolean hasConnectivityState() {
        return true;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        // NO OP
    }

    @Nonnull
    @Override
    public FilterConfig getConfig() {
        return this.config;
    }

    @Override
    public NBTTagCompound writeExtraNbt(NBTTagCompound tag) {
        return tag;
    }

    @Override
    public void readExtraNbt(NBTTagCompound tag) {
        //NO OP
    }
}
