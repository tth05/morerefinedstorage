package com.raoulvdberge.refinedstorage.apiimpl.network.node.storage;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.IGuiStorage;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheItem;
import com.raoulvdberge.refinedstorage.block.BlockStorage;
import com.raoulvdberge.refinedstorage.block.enums.ItemStorageType;
import com.raoulvdberge.refinedstorage.tile.TileStorage;
import com.raoulvdberge.refinedstorage.tile.config.IAccessType;
import com.raoulvdberge.refinedstorage.tile.config.IPrioritizable;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.util.AccessTypeUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class NetworkNodeStorage extends NetworkNode implements IGuiStorage, IStorageProvider, IRSFilterConfigProvider, IPrioritizable, IAccessType, IStorageDiskContainerContext {
    public static final String ID = "storage";

    private static final String NBT_PRIORITY = "Priority";
    public static final String NBT_ID = "Id";

    private ItemStorageType type;

    private AccessType accessType = AccessType.INSERT_EXTRACT;
    private int priority = 0;
    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterTypeItems()
            .allowedFilterModeBlackAndWhitelist()
            .filterModeBlacklist()
            .filterSizeNine()
            .compareDamageAndNbt().build();

    private UUID storageId = UUID.randomUUID();
    private IStorageDisk<ItemStack> storage;

    public NetworkNodeStorage(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.storageUsage;
    }

    @Override
    public void onConnectedStateChange(INetwork network, boolean state) {
        super.onConnectedStateChange(network, state);

        network.getNodeGraph().runActionWhenPossible(StorageCacheItem.INVALIDATE);
    }

    @Override
    public void addItemStorages(List<IStorage<ItemStack>> storages) {
        if (storage == null) {
            loadStorage();
        }

        storages.add(storage);
    }

    @Override
    public void addFluidStorages(List<IStorage<FluidStack>> storages) {
        // NO OP
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        tag.setUniqueId(NBT_ID, storageId);

        return tag;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        if (tag.hasUniqueId(NBT_ID)) {
            storageId = tag.getUniqueId(NBT_ID);

            loadStorage();
        }
    }

    public void loadStorage() {
        IStorageDisk<?> disk = API.instance().getStorageDiskManager(world).get(storageId);

        if (disk == null) {
            disk = API.instance().createDefaultItemDisk(world, getType().getCapacity());
            API.instance().getStorageDiskManager(world).set(storageId, disk);
            API.instance().getStorageDiskManager(world).markForSaving();
        }

        this.storage = new StorageDiskItemStorageWrapper(this, (IStorageDisk<ItemStack>) disk);
    }

    public void setStorageId(UUID id) {
        this.storageId = id;

        markNetworkNodeDirty();
    }

    public UUID getStorageId() {
        return storageId;
    }

    public IStorageDisk<ItemStack> getStorage() {
        return storage;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        tag.setInteger(NBT_PRIORITY, priority);

        AccessTypeUtils.writeAccessType(tag, accessType);

        tag.setTag("config", this.config.writeToNBT(new NBTTagCompound()));
        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_PRIORITY)) {
            priority = tag.getInteger(NBT_PRIORITY);
        }

        accessType = AccessTypeUtils.readAccessType(tag);

        this.config.readFromNBT(tag.getCompoundTag("config"));
    }

    public ItemStorageType getType() {
        if (type == null && world != null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == RSBlocks.STORAGE) {
                type = state.getValue(BlockStorage.TYPE);
            }
        }

        return type == null ? ItemStorageType.TYPE_1K : type;
    }

    @Override
    public String getGuiTitle() {
        return "block.refinedstorage:storage." + getType().getId() + ".name";
    }

    @Override
    public TileDataParameter<Integer, ?> getTypeParameter() {
        return null;
    }

    @Override
    public TileDataParameter<Integer, ?> getRedstoneModeParameter() {
        return TileStorage.REDSTONE_MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getCompareParameter() {
        return TileStorage.COMPARE;
    }

    @Override
    public TileDataParameter<Integer, ?> getFilterParameter() {
        return TileStorage.MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getPriorityParameter() {
        return TileStorage.PRIORITY;
    }

    @Override
    public TileDataParameter<AccessType, ?> getAccessTypeParameter() {
        return TileStorage.ACCESS_TYPE;
    }

    @Override
    public long getStored() {
        return TileStorage.STORED.getValue();
    }

    @Override
    public long getCapacity() {
        return getType().getCapacity();
    }

    @Override
    public AccessType getAccessType() {
        return accessType;
    }

    @Override
    public void setAccessType(AccessType value) {
        this.accessType = value;

        if (network != null) {
            network.getItemStorageCache().invalidate();
        }

        markNetworkNodeDirty();
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;

        markNetworkNodeDirty();

        if (network != null) {
            network.getItemStorageCache().sort();
        }
    }

    @Nonnull
    @Override
    public FilterConfig getConfig() {
        return this.config;
    }

    @Override
    public NBTTagCompound writeExtraNbt(NBTTagCompound tag) {
        tag.setInteger("accessType", this.accessType.getId());
        return tag;
    }

    @Override
    public void readExtraNbt(NBTTagCompound tag) {
        if (tag.hasKey("accessType")) {
            this.accessType = AccessTypeUtils.getAccessType(tag.getInteger("accessType"));
        }
    }
}
