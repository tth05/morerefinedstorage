package com.raoulvdberge.refinedstorage.apiimpl.network.node.storage;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.IGuiStorage;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheFluid;
import com.raoulvdberge.refinedstorage.block.BlockFluidStorage;
import com.raoulvdberge.refinedstorage.block.enums.FluidStorageType;
import com.raoulvdberge.refinedstorage.tile.TileFluidStorage;
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

public class NetworkNodeFluidStorage extends NetworkNode implements IGuiStorage, IStorageProvider, IRSFilterConfigProvider, IPrioritizable, IAccessType, IStorageDiskContainerContext {
    public static final String ID = "fluid_storage";

    private static final String NBT_PRIORITY = "Priority";
    public static final String NBT_ID = "Id";

    private FluidStorageType type;

    private AccessType accessType = AccessType.INSERT_EXTRACT;
    private int priority = 0;
    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterTypeFluids()
            .allowedFilterModeBlackAndWhitelist()
            .filterModeBlacklist()
            .filterSizeNine()
            .setInitialCompare(IComparer.COMPARE_NBT).build();

    private UUID storageId = UUID.randomUUID();
    private IStorageDisk<FluidStack> storage;

    public NetworkNodeFluidStorage(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.fluidStorageUsage;
    }

    @Override
    public void onConnectedStateChange(INetwork network, boolean state) {
        super.onConnectedStateChange(network, state);

        network.getNodeGraph().runActionWhenPossible(StorageCacheFluid.INVALIDATE);
    }

    @Override
    public void addItemStorages(List<IStorage<ItemStack>> storages) {
        // NO OP
    }

    @Override
    public void addFluidStorages(List<IStorage<FluidStack>> storages) {
        if (storage == null) {
            loadStorage();
        }

        storages.add(storage);
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
            disk = API.instance().createDefaultFluidDisk(world, getType().getCapacity());
            API.instance().getStorageDiskManager(world).set(storageId, disk);
            API.instance().getStorageDiskManager(world).markForSaving();
        }

        this.storage = new StorageDiskFluidStorageWrapper(this, (IStorageDisk<FluidStack>) disk);
    }

    public void setStorageId(UUID id) {
        this.storageId = id;

        markNetworkNodeDirty();
    }

    public UUID getStorageId() {
        return storageId;
    }

    public IStorageDisk<FluidStack> getStorage() {
        return storage;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        tag.setInteger(NBT_PRIORITY, priority);

        AccessTypeUtils.writeAccessType(tag, accessType);

        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_PRIORITY)) {
            priority = tag.getInteger(NBT_PRIORITY);
        }

        accessType = AccessTypeUtils.readAccessType(tag);
    }

    public FluidStorageType getType() {
        if (type == null && world != null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == RSBlocks.FLUID_STORAGE) {
                type = state.getValue(BlockFluidStorage.TYPE);
            }
        }

        return type == null ? FluidStorageType.TYPE_64K : type;
    }

    @Override
    public String getGuiTitle() {
        return "block.refinedstorage:fluid_storage." + getType().getId() + ".name";
    }

    @Override
    public TileDataParameter<Integer, ?> getTypeParameter() {
        return null;
    }

    @Override
    public TileDataParameter<Integer, ?> getRedstoneModeParameter() {
        return TileFluidStorage.REDSTONE_MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getCompareParameter() {
        return TileFluidStorage.COMPARE;
    }

    @Override
    public TileDataParameter<Integer, ?> getFilterParameter() {
        return TileFluidStorage.MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getPriorityParameter() {
        return TileFluidStorage.PRIORITY;
    }

    @Override
    public TileDataParameter<AccessType, ?> getAccessTypeParameter() {
        return TileFluidStorage.ACCESS_TYPE;
    }

    @Override
    public long getStored() {
        return TileFluidStorage.STORED.getValue();
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
            network.getFluidStorageCache().invalidate();
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
            network.getFluidStorageCache().sort();
        }
    }

    @Nonnull
    @Override
    public FilterConfig getConfig() {
        return this.config;
    }
}
