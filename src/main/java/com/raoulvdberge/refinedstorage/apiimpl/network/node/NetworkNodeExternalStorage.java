package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.StorageType;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheFluid;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheItem;
import com.raoulvdberge.refinedstorage.tile.TileExternalStorage;
import com.raoulvdberge.refinedstorage.tile.config.*;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.util.AccessTypeUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NetworkNodeExternalStorage extends NetworkNode implements IStorageProvider, IGuiStorage, IPrioritizable, IRSFilterConfigProvider, IAccessType, IExternalStorageContext, ICoverable {
    public static final String ID = "external_storage";

    private static final String NBT_PRIORITY = "Priority";
    private static final String NBT_COVERS = "Covers";

    private int priority = 0;
    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterModeBlackAndWhitelist()
            .filterModeBlacklist()
            .allowedFilterTypeItemsAndFluids()
            .filterTypeItems()
            .filterSizeNine()
            .compareDamageAndNbt()
            .customFilterTypeSupplier(ft -> world.isRemote ? FilterType.values()[TileExternalStorage.TYPE.getValue()] : ft)
            .onFilterTypeChanged(ft -> {
                if (network != null)
                    updateStorage(network);
            }).build();
    private AccessType accessType = AccessType.INSERT_EXTRACT;
    private int networkTicks;

    private final CoverManager coverManager = new CoverManager(this);

    private final List<IStorageExternal<ItemStack>> itemStorages = new CopyOnWriteArrayList<>();
    private final List<IStorageExternal<FluidStack>> fluidStorages = new CopyOnWriteArrayList<>();

    public NetworkNodeExternalStorage(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.externalStorageUsage + ((itemStorages.size() + fluidStorages.size()) * RS.INSTANCE.config.externalStoragePerStorageUsage);
    }

    @Override
    public void onConnectedStateChange(INetwork network, boolean state) {
        super.onConnectedStateChange(network, state);

        updateStorage(network);
    }

    @Override
    public void updateNetworkNode() {
        super.updateNetworkNode();

        if (canUpdate()) {
            if (networkTicks++ == 0) {
                updateStorage(network);

                return;
            }

            for (IStorageExternal<ItemStack> storage : itemStorages) {
                storage.update(network);
            }

            for (IStorageExternal<FluidStack> storage : fluidStorages) {
                storage.update(network);
            }
        }
    }

    @Override
    protected void onDirectionChanged() {
        super.onDirectionChanged();

        if (network != null) {
            updateStorage(network);
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        if (tag.hasKey(NBT_COVERS)) {
            coverManager.readFromNbt(tag.getTagList(NBT_COVERS, Constants.NBT.TAG_COMPOUND));
        }
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        tag.setTag(NBT_COVERS, coverManager.writeToNbt());

        return tag;
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
            network.getFluidStorageCache().sort();
        }
    }

    public void updateStorage(INetwork network) {
        itemStorages.clear();
        fluidStorages.clear();

        TileEntity facing = getFacingTile();

        if (facing != null) {
            if (config.isFilterTypeItem()) {
                for (IExternalStorageProvider provider : API.instance().getExternalStorageProviders(StorageType.ITEM)) {
                    if (provider.canProvide(facing, getDirection())) {
                        itemStorages.add(provider.provide(this, this::getFacingTile, getDirection()));

                        break;
                    }
                }
            } else if (config.isFilterTypeFluid()) {
                for (IExternalStorageProvider provider : API.instance().getExternalStorageProviders(StorageType.FLUID)) {
                    if (provider.canProvide(facing, getDirection())) {
                        fluidStorages.add(provider.provide(this, this::getFacingTile, getDirection()));

                        break;
                    }
                }
            }
        }

        network.getNodeGraph().runActionWhenPossible(StorageCacheItem.INVALIDATE);
        network.getNodeGraph().runActionWhenPossible(StorageCacheFluid.INVALIDATE);
    }

    @Override
    public void addItemStorages(List<IStorage<ItemStack>> storages) {
        storages.addAll(this.itemStorages);
    }

    @Override
    public void addFluidStorages(List<IStorage<FluidStack>> storages) {
        storages.addAll(this.fluidStorages);
    }

    @Override
    public String getGuiTitle() {
        return "gui.refinedstorage:external_storage";
    }

    @Override
    public TileDataParameter<Integer, ?> getRedstoneModeParameter() {
        return TileExternalStorage.REDSTONE_MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getCompareParameter() {
        return TileExternalStorage.COMPARE;
    }

    @Override
    public TileDataParameter<Integer, ?> getFilterParameter() {
        return TileExternalStorage.MODE;
    }

    @Override
    public TileDataParameter<Integer, ?> getPriorityParameter() {
        return TileExternalStorage.PRIORITY;
    }

    @Override
    public TileDataParameter<AccessType, ?> getAccessTypeParameter() {
        return TileExternalStorage.ACCESS_TYPE;
    }

    @Override
    public long getStored() {
        return TileExternalStorage.STORED.getValue();
    }

    @Override
    public long getCapacity() {
        return TileExternalStorage.CAPACITY.getValue();
    }

    @Override
    public AccessType getAccessType() {
        return accessType;
    }

    @Override
    public boolean acceptsItem(ItemStack stack) {
        return config.acceptsItem(stack);
    }

    @Override
    public boolean acceptsFluid(FluidStack stack) {
        return config.acceptsFluid(stack);
    }

    @Override
    public void setAccessType(AccessType type) {
        this.accessType = type;

        if (network != null) {
            network.getItemStorageCache().invalidate();
            network.getFluidStorageCache().invalidate();
        }

        markNetworkNodeDirty();
    }

    @Override
    public TileDataParameter<Integer, ?> getTypeParameter() {
        return TileExternalStorage.TYPE;
    }

    public List<IStorageExternal<ItemStack>> getItemStorages() {
        return itemStorages;
    }

    public List<IStorageExternal<FluidStack>> getFluidStorages() {
        return fluidStorages;
    }

    @Override
    public boolean canConduct(@Nullable EnumFacing direction) {
        return coverManager.canConduct(direction);
    }

    @Nullable
    @Override
    public IItemHandler getDrops() {
        return coverManager.getAsInventory();
    }

    @Override
    public CoverManager getCoverManager() {
        return coverManager;
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
