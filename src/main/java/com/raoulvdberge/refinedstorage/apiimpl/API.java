package com.raoulvdberge.refinedstorage.apiimpl;

import com.raoulvdberge.refinedstorage.api.IRSAPI;
import com.raoulvdberge.refinedstorage.api.RSAPIInject;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternRenderHandler;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementList;
import com.raoulvdberge.refinedstorage.api.autocrafting.craftingmonitor.ICraftingMonitorElementRegistry;
import com.raoulvdberge.refinedstorage.api.autocrafting.preview.ICraftingPreviewElementRegistry;
import com.raoulvdberge.refinedstorage.api.autocrafting.registry.ICraftingTaskRegistry;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.CraftingTaskReadException;
import com.raoulvdberge.refinedstorage.api.autocrafting.engine.ICraftingRequestInfo;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.grid.ICraftingGridBehavior;
import com.raoulvdberge.refinedstorage.api.network.grid.IGridManager;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeManager;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeRegistry;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IReaderWriterChannel;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IReaderWriterHandlerRegistry;
import com.raoulvdberge.refinedstorage.api.storage.StorageType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskManager;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskRegistry;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskSync;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IExternalStorageProvider;
import com.raoulvdberge.refinedstorage.api.util.*;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.CraftingRequestInfo;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementList;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.craftingmonitor.CraftingMonitorElementRegistry;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.preview.CraftingPreviewElementRegistry;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.registry.CraftingTaskRegistry;
import com.raoulvdberge.refinedstorage.apiimpl.network.NetworkNodeManager;
import com.raoulvdberge.refinedstorage.apiimpl.network.NetworkNodeRegistry;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.CraftingGridBehavior;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.GridManager;
import com.raoulvdberge.refinedstorage.apiimpl.network.readerwriter.ReaderWriterChannel;
import com.raoulvdberge.refinedstorage.apiimpl.network.readerwriter.ReaderWriterHandlerRegistry;
import com.raoulvdberge.refinedstorage.apiimpl.storage.disk.*;
import com.raoulvdberge.refinedstorage.apiimpl.util.*;
import com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;

public class API implements IRSAPI {
    private static final IRSAPI INSTANCE = new API();

    private final IComparer comparer = new Comparer();
    private final IQuantityFormatter quantityFormatter = new QuantityFormatter();
    private final INetworkNodeRegistry networkNodeRegistry = new NetworkNodeRegistry();
    private final ICraftingTaskRegistry craftingTaskRegistry = new CraftingTaskRegistry();
    private final ICraftingMonitorElementRegistry craftingMonitorElementRegistry = new CraftingMonitorElementRegistry();
    private final ICraftingPreviewElementRegistry craftingPreviewElementRegistry = new CraftingPreviewElementRegistry();
    private final IReaderWriterHandlerRegistry readerWriterHandlerRegistry = new ReaderWriterHandlerRegistry();
    private final IGridManager gridManager = new GridManager();
    private final ICraftingGridBehavior craftingGridBehavior = new CraftingGridBehavior();
    private final IStorageDiskRegistry storageDiskRegistry = new StorageDiskRegistry();
    private final IStorageDiskSync storageDiskSync = new StorageDiskSync();
    private final IOneSixMigrationHelper oneSixMigrationHelper = new OneSixMigrationHelper();
    private final Map<StorageType, TreeSet<IExternalStorageProvider<?>>> externalStorageProviders =
            new EnumMap<>(StorageType.class);
    private final List<ICraftingPatternRenderHandler> patternRenderHandlers = new LinkedList<>();

    public static IRSAPI instance() {
        return INSTANCE;
    }

    public static void deliver(ASMDataTable asmDataTable) {
        String annotationClassName = RSAPIInject.class.getCanonicalName();

        Set<ASMDataTable.ASMData> asmDataSet = asmDataTable.getAll(annotationClassName);

        for (ASMDataTable.ASMData asmData : asmDataSet) {
            try {
                Class<?> clazz = Class.forName(asmData.getClassName());
                Field field = clazz.getField(asmData.getObjectName());

                if (field.getType() == IRSAPI.class) {
                    field.set(null, INSTANCE);
                }
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to set: {}" + asmData.getClassName() + "." + asmData.getObjectName(),
                        e);
            }
        }
    }

    @Nonnull
    @Override
    public IComparer getComparer() {
        return comparer;
    }

    @Override
    @Nonnull
    public IQuantityFormatter getQuantityFormatter() {
        return quantityFormatter;
    }

    @Override
    @Nonnull
    public INetworkNodeRegistry getNetworkNodeRegistry() {
        return networkNodeRegistry;
    }

    @Override
    public INetworkNodeManager getNetworkNodeManager(World world) {
        if (world.isRemote) {
            throw new IllegalArgumentException("Attempting to access network node manager on the client");
        }

        MapStorage storage = world.getPerWorldStorage();
        NetworkNodeManager instance =
                (NetworkNodeManager) storage.getOrLoadData(NetworkNodeManager.class, NetworkNodeManager.NAME);

        if (instance == null) {
            instance = new NetworkNodeManager(NetworkNodeManager.NAME);

            storage.setData(NetworkNodeManager.NAME, instance);
        } else {
            instance.tryReadNodes(world);
        }

        return instance;
    }

    @Override
    @Nonnull
    public ICraftingTaskRegistry getCraftingTaskRegistry() {
        return craftingTaskRegistry;
    }

    @Override
    @Nonnull
    public ICraftingMonitorElementRegistry getCraftingMonitorElementRegistry() {
        return craftingMonitorElementRegistry;
    }

    @Override
    @Nonnull
    public ICraftingPreviewElementRegistry getCraftingPreviewElementRegistry() {
        return craftingPreviewElementRegistry;
    }

    @Nonnull
    @Override
    public IReaderWriterHandlerRegistry getReaderWriterHandlerRegistry() {
        return readerWriterHandlerRegistry;
    }

    @Nonnull
    @Override
    public IReaderWriterChannel createReaderWriterChannel(String name, INetwork network) {
        return new ReaderWriterChannel(name, network);
    }

    @Nonnull
    @Override
    public IStackList<ItemStack> createItemStackList() {
        return new StackListItem();
    }

    @Override
    @Nonnull
    public IStackList<FluidStack> createFluidStackList() {
        return new StackListFluid();
    }

    @Override
    @Nonnull
    public ICraftingMonitorElementList createCraftingMonitorElementList() {
        return new CraftingMonitorElementList();
    }

    @Nonnull
    @Override
    public IGridManager getGridManager() {
        return gridManager;
    }

    @Nonnull
    @Override
    public ICraftingGridBehavior getCraftingGridBehavior() {
        return craftingGridBehavior;
    }

    @Nonnull
    @Override
    public IStorageDiskRegistry getStorageDiskRegistry() {
        return storageDiskRegistry;
    }

    @Nonnull
    @Override
    public IStorageDiskManager getStorageDiskManager(World world) {
        if (world.isRemote) {
            throw new IllegalArgumentException("Attempting to access storage disk manager on the client");
        }

        MapStorage storage = world.getMapStorage();
        StorageDiskManager instance =
                (StorageDiskManager) storage.getOrLoadData(StorageDiskManager.class, StorageDiskManager.NAME);

        if (instance == null) {
            instance = new StorageDiskManager(StorageDiskManager.NAME);

            storage.setData(StorageDiskManager.NAME, instance);
        } else {
            instance.tryReadDisks(world);
        }

        return instance;
    }

    @Nonnull
    @Override
    public IStorageDiskSync getStorageDiskSync() {
        return storageDiskSync;
    }

    @Override
    public void addExternalStorageProvider(StorageType type, IExternalStorageProvider<?> provider) {
        externalStorageProviders
                .computeIfAbsent(type, k -> new TreeSet<>((a, b) -> Integer.compare(b.getPriority(), a.getPriority())))
                .add(provider);
    }

    @Override
    public Set<IExternalStorageProvider<?>> getExternalStorageProviders(StorageType type) {
        TreeSet<IExternalStorageProvider<?>> providers = externalStorageProviders.get(type);

        return providers == null ? Collections.emptySet() : providers;
    }

    @Override
    @Nonnull
    public IStorageDisk<ItemStack> createDefaultItemDisk(World world, int capacity) {
        return new StorageDiskItem(world, capacity);
    }

    @Override
    @Nonnull
    public IStorageDisk<FluidStack> createDefaultFluidDisk(World world, int capacity) {
        return new StorageDiskFluid(world, capacity);
    }

    @Override
    public ICraftingRequestInfo createCraftingRequestInfo(ItemStack stack) {
        return new CraftingRequestInfo(stack);
    }

    @Override
    public ICraftingRequestInfo createCraftingRequestInfo(FluidStack stack) {
        return new CraftingRequestInfo(stack);
    }

    @Override
    public ICraftingRequestInfo createCraftingRequestInfo(NBTTagCompound tag) throws CraftingTaskReadException {
        return new CraftingRequestInfo(tag);
    }

    @Override
    @Nonnull
    public IOneSixMigrationHelper getOneSixMigrationHelper() {
        return oneSixMigrationHelper;
    }

    @Override
    public void addPatternRenderHandler(ICraftingPatternRenderHandler renderHandler) {
        patternRenderHandlers.add(renderHandler);
    }

    @Override
    public List<ICraftingPatternRenderHandler> getPatternRenderHandlers() {
        return patternRenderHandlers;
    }

    @Override
    public void discoverNode(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            TileEntity tile = world.getTileEntity(pos.offset(facing));

            if (tile != null && tile.hasCapability(CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY,
                    facing.getOpposite())) {
                INetworkNodeProxy<?> nodeProxy = tile.getCapability(
                        CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY,
                                facing.getOpposite());
                INetworkNode node = nodeProxy.getNode();

                if (node.getNetwork() == null)
                    continue;

                node.getNetwork().getNodeGraph()
                        .invalidate(Action.PERFORM, node.getNetwork().world(), node.getNetwork().getPosition());
                return;
            }
        }
    }

    @Override
    public int getItemStackHashCode(ItemStack stack) {
        int result = stack.getItem().hashCode();
        result = 31 * result + (stack.getItemDamage() + 1);

        if (stack.hasTagCompound()) {
            result = getHashCode(stack.getTagCompound(), result);
        }

        return result;
    }

    private int getHashCode(NBTBase tag, int result) {
        if (tag instanceof NBTTagCompound) {
            result = getHashCode((NBTTagCompound) tag, result);
        } else if (tag instanceof NBTTagList) {
            result = getHashCode((NBTTagList) tag, result);
        } else {
            result = 31 * result + tag.hashCode();
        }

        return result;
    }

    private int getHashCode(NBTTagCompound tag, int result) {
        for (String key : tag.getKeySet()) {
            result = 31 * result + key.hashCode();
            result = getHashCode(tag.getTag(key), result);
        }

        return result;
    }

    private int getHashCode(NBTTagList tag, int result) {
        for (int i = 0; i < tag.tagCount(); ++i) {
            result = getHashCode(tag.get(i), result);
        }

        return result;
    }

    @Override
    public int getFluidStackHashCode(FluidStack stack) {
        int result = stack.getFluid().hashCode();

        if (stack.tag != null) {
            result = getHashCode(stack.tag, result);
        }

        return result;
    }

    @Override
    public int getNetworkNodeHashCode(INetworkNode node) {
        int result = node.getPos().hashCode();
        result = 31 * result + node.getWorld().provider.getDimension();

        return result;
    }

    @Override
    public boolean isNetworkNodeEqual(INetworkNode left, Object right) {
        if (!(right instanceof INetworkNode)) {
            return false;
        }

        if (left == right) {
            return true;
        }

        INetworkNode rightNode = (INetworkNode) right;

        if (left.getWorld().provider.getDimension() != rightNode.getWorld().provider.getDimension()) {
            return false;
        }

        return left.getPos().equals(rightNode.getPos());
    }
}
