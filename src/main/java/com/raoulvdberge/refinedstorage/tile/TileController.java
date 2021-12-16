package com.raoulvdberge.refinedstorage.tile;

import com.google.common.base.Preconditions;
import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingManager;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.energy.IEnergy;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.INetworkNodeGraph;
import com.raoulvdberge.refinedstorage.api.network.INetworkNodeVisitor;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IFluidGridHandler;
import com.raoulvdberge.refinedstorage.api.network.grid.handler.IItemGridHandler;
import com.raoulvdberge.refinedstorage.api.network.item.INetworkItemHandler;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IReaderWriterListener;
import com.raoulvdberge.refinedstorage.api.network.readerwriter.IReaderWriterManager;
import com.raoulvdberge.refinedstorage.api.network.security.ISecurityManager;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.externalstorage.IStorageExternal;
import com.raoulvdberge.refinedstorage.api.storage.tracker.IStorageTracker;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.CraftingManager;
import com.raoulvdberge.refinedstorage.apiimpl.energy.Energy;
import com.raoulvdberge.refinedstorage.apiimpl.network.NetworkNodeGraph;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.FluidGridHandler;
import com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler;
import com.raoulvdberge.refinedstorage.apiimpl.network.item.NetworkItemHandler;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.ICoverable;
import com.raoulvdberge.refinedstorage.apiimpl.network.readerwriter.ReaderWriterManager;
import com.raoulvdberge.refinedstorage.apiimpl.network.security.SecurityManager;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheFluid;
import com.raoulvdberge.refinedstorage.apiimpl.storage.cache.StorageCacheItem;
import com.raoulvdberge.refinedstorage.apiimpl.storage.tracker.StorageTrackerFluid;
import com.raoulvdberge.refinedstorage.apiimpl.storage.tracker.StorageTrackerItem;
import com.raoulvdberge.refinedstorage.block.BlockController;
import com.raoulvdberge.refinedstorage.block.enums.ControllerEnergyType;
import com.raoulvdberge.refinedstorage.block.enums.ControllerType;
import com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy;
import com.raoulvdberge.refinedstorage.integration.forgeenergy.EnergyProxy;
import com.raoulvdberge.refinedstorage.tile.config.IRedstoneConfigurable;
import com.raoulvdberge.refinedstorage.tile.config.RedstoneMode;
import com.raoulvdberge.refinedstorage.tile.data.RSSerializers;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY;

// TODO: Change INetwork to be offloaded from the tile.
public class TileController extends TileBase
        implements ITickable, INetwork, IRedstoneConfigurable, INetworkNode, INetworkNodeProxy<TileController>,
        INetworkNodeVisitor {
    private static final Comparator<ClientNode> CLIENT_NODE_COMPARATOR = (left, right) -> {
        if (left.getEnergyUsage() == right.getEnergyUsage()) {
            return 0;
        }

        return (left.getEnergyUsage() > right.getEnergyUsage()) ? -1 : 1;
    };

    public static final TileDataParameter<Integer, TileController> REDSTONE_MODE = RedstoneMode.createParameter();
    public static final TileDataParameter<Integer, TileController> ENERGY_USAGE =
            new TileDataParameter<>(DataSerializers.VARINT, 0, TileController::getEnergyUsage);
    public static final TileDataParameter<Integer, TileController> ENERGY_STORED =
            new TileDataParameter<>(DataSerializers.VARINT, 0, t -> t.getEnergy().getStored());
    public static final TileDataParameter<Integer, TileController> ENERGY_CAPACITY =
            new TileDataParameter<>(DataSerializers.VARINT, 0, t -> t.getEnergy().getCapacity());
    public static final TileDataParameter<List<ClientNode>, TileController> NODES =
            new TileDataParameter<>(RSSerializers.CLIENT_NODE_SERIALIZER, new ArrayList<>(), t -> {
                List<ClientNode> nodes = new ArrayList<>();

                for (INetworkNode node : t.nodeGraph.allActualNodes()) {
                    if (node.canUpdate()) {
                        ItemStack stack = node.getItemStack();

                        if (stack.isEmpty()) {
                            continue;
                        }

                        ClientNode clientNode = new ClientNode(stack, 1, node.getEnergyUsage());

                        if (nodes.contains(clientNode)) {
                            ClientNode other = nodes.get(nodes.indexOf(clientNode));

                            other.setAmount(other.getAmount() + 1);
                        } else {
                            nodes.add(clientNode);
                        }
                    }
                }

                nodes.sort(CLIENT_NODE_COMPARATOR);

                return nodes;
            });

    private static final int THROTTLE_INACTIVE_TO_ACTIVE = 20;
    private static final int THROTTLE_ACTIVE_TO_INACTIVE = 4;

    public static final String NBT_ENERGY = "Energy";
    public static final String NBT_ENERGY_TYPE = "EnergyType";

    private static final String NBT_ITEM_STORAGE_TRACKER = "ItemStorageTracker";
    private static final String NBT_FLUID_STORAGE_TRACKER = "FluidStorageTracker";

    private final IItemGridHandler itemGridHandler = new ItemGridHandler(this);
    private final IFluidGridHandler fluidGridHandler = new FluidGridHandler(this);

    private final INetworkItemHandler networkItemHandler = new NetworkItemHandler(this);

    private final INetworkNodeGraph nodeGraph = new NetworkNodeGraph(this);

    private final ICraftingManager craftingManager = new CraftingManager(this);

    private final ISecurityManager securityManager = new SecurityManager(this);

    private final IStorageCache<ItemStack> itemStorage = new StorageCacheItem(this);
    private final StorageTrackerItem itemStorageTracker = new StorageTrackerItem(this::markNetworkNodeDirty);

    private final IStorageCache<FluidStack> fluidStorage = new StorageCacheFluid(this);
    private final StorageTrackerFluid fluidStorageTracker = new StorageTrackerFluid(this::markNetworkNodeDirty);

    private final IReaderWriterManager readerWriterManager = new ReaderWriterManager(this);

    private final IEnergy energy = new Energy(RS.INSTANCE.config.controllerCapacity);
    private final EnergyProxy energyProxy = new EnergyProxy(this.energy, RS.INSTANCE.config.controllerMaxReceive, 0);

    private boolean throttlingDisabled = true; // Will be enabled after first update
    private boolean couldRun;
    private int ticksSinceUpdateChanged;
    private int lastEnergyUsage;

    private ControllerType type;
    private ControllerEnergyType energyType = ControllerEnergyType.OFF;

    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private boolean redstoneModeEnabled;

    public TileController() {
        dataManager.addWatchedParameter(REDSTONE_MODE);
        dataManager.addWatchedParameter(ENERGY_USAGE);
        dataManager.addWatchedParameter(ENERGY_STORED);
        dataManager.addParameter(ENERGY_CAPACITY);
        dataManager.addParameter(NODES);

        readerWriterManager.addListener(new IReaderWriterListener() {
            @Override
            public void onAttached() {
            }

            @Override
            public void onChanged() {
                markNetworkNodeDirty();
            }
        });

        nodeGraph.addListener(() -> dataManager.sendParameterToWatchers(TileController.NODES));
    }

    @Override
    public void updateNetworkNode() {
        //NO OP
    }

    @Override
    public void update() {
        if (!world.isRemote) {
            updateEnergyUsage();
            updateRedstoneMode();

            boolean canRun = canRun();

            if (canRun) {
                craftingManager.update();

                readerWriterManager.update();

                if (!craftingManager.getTasks().isEmpty()) {
                    markNetworkNodeDirty();
                }
            }

            if (getType() == ControllerType.NORMAL) {
                int energyUsage = getEnergyUsage();

                if (!RS.INSTANCE.config.controllerUsesEnergy) {
                    this.energy.setStored(this.energy.getCapacity());
                } else if (this.energy.extract(energyUsage, Action.SIMULATE) == energyUsage) {
                    this.energy.extract(energyUsage, Action.PERFORM);
                }
            } else if (getType() == ControllerType.CREATIVE) {
                this.energy.setStored(this.energy.getCapacity());
            }

            if (couldRun != canRun) {
                ++ticksSinceUpdateChanged;

                if ((canRun ? (ticksSinceUpdateChanged > THROTTLE_INACTIVE_TO_ACTIVE) :
                        (ticksSinceUpdateChanged > THROTTLE_ACTIVE_TO_INACTIVE)) || throttlingDisabled) {
                    ticksSinceUpdateChanged = 0;
                    couldRun = canRun;
                    throttlingDisabled = false;

                    nodeGraph.invalidate(Action.PERFORM, world, pos);
                    securityManager.invalidate();
                }
            } else {
                ticksSinceUpdateChanged = 0;
            }

            ControllerEnergyType energyType = getEnergyType();

            if (this.energyType != energyType) {
                this.energyType = energyType;

                WorldUtils.updateBlock(world, pos);
            }
        }
    }

    @Override
    public boolean canRun() {
        return this.energy.getStored() >= this.getEnergyUsage() && this.redstoneModeEnabled;
    }

    @Nullable
    @Override
    public synchronized StackListResult<ItemStack> insertItem(@Nonnull ItemStack stack, long size, Action action) {
        if (size < 1 || itemStorage.getStorages().isEmpty()) {
            return new StackListResult<>(stack.copy(), size);
        }

        StackListResult<ItemStack> remainder = null;

        long inserted = 0;
        long insertedExternally = 0;

        for (IStorage<ItemStack> storage : this.itemStorage.getStorages()) {
            if (storage.getAccessType() == AccessType.EXTRACT) {
                continue;
            }

            long storedPre = storage.getStored();

            remainder = storage.insert(stack, size, action);

            if (action == Action.PERFORM) {
                inserted += storage.getCacheDelta(storedPre, size, remainder == null ? 0 : remainder.getCount());
            }

            if (remainder == null) {
                // The external storage is responsible for sending changes, we don't need to anymore
                if (storage instanceof IStorageExternal && action == Action.PERFORM) {
                    ((IStorageExternal<?>) storage).update(this);

                    insertedExternally += size;
                }

                break;
            } else {
                // The external storage is responsible for sending changes, we don't need to anymore
                if (size != remainder.getCount() && storage instanceof IStorageExternal && action == Action.PERFORM) {
                    ((IStorageExternal<?>) storage).update(this);

                    insertedExternally += size - remainder.getCount();
                }

                size = remainder.getCount();
            }
        }

        if (action == Action.PERFORM && inserted - insertedExternally > 0) {
            itemStorage.add(stack, inserted - insertedExternally, false);
        }

        return remainder;
    }

    @Nullable
    @Override
    public synchronized StackListResult<ItemStack> extractItem(@Nonnull ItemStack stack, long size, int flags, Action action,
                                                               Predicate<IStorage<ItemStack>> filter) {
        long received = 0;
        long extractedExternally = 0;

        StackListResult<ItemStack> newStack = null;

        for (IStorage<ItemStack> storage : this.itemStorage.getStorages()) {
            StackListResult<ItemStack> took = null;

            if (filter.test(storage) && storage.getAccessType() != AccessType.INSERT) {
                took = storage.extract(stack, size - received, flags, action);
            }

            if (took != null) {
                // The external storage is responsible for sending changes, we don't need to anymore
                if (storage instanceof IStorageExternal && action == Action.PERFORM) {
                    ((IStorageExternal<?>) storage).update(this);

                    extractedExternally += took.getCount();
                }

                if (newStack == null) {
                    newStack = took;
                } else {
                    newStack.grow(took.getCount());
                }

                received += took.getCount();
            }

            if (size == received) {
                break;
            }
        }

        if (newStack != null && newStack.getCount() - extractedExternally > 0 && action == Action.PERFORM) {
            itemStorage.remove(newStack.getStack(), newStack.getCount() - extractedExternally, false);
        }

        return newStack;
    }

    @Nullable
    @Override
    public synchronized StackListResult<FluidStack> insertFluid(@Nonnull FluidStack stack, long size, Action action) {
        if (size < 1 || fluidStorage.getStorages().isEmpty()) {
            return new StackListResult<>(StackUtils.copy(stack, 0), size);
        }

        StackListResult<FluidStack> remainder = null;

        long inserted = 0;
        long insertedExternally = 0;

        for (IStorage<FluidStack> storage : this.fluidStorage.getStorages()) {
            if (storage.getAccessType() == AccessType.EXTRACT) {
                continue;
            }

            long storedPre = storage.getStored();

            remainder = storage.insert(stack, size, action);

            if (action == Action.PERFORM) {
                inserted += storage.getCacheDelta(storedPre, size, remainder == null ? 0 : remainder.getCount());
            }

            if (remainder == null) {
                // The external storage is responsible for sending changes, we don't need to anymore
                if (storage instanceof IStorageExternal && action == Action.PERFORM) {
                    ((IStorageExternal<?>) storage).update(this);

                    insertedExternally += size;
                }

                break;
            } else {
                // The external storage is responsible for sending changes, we don't need to anymore
                if (size != remainder.getCount() && storage instanceof IStorageExternal && action == Action.PERFORM) {
                    ((IStorageExternal<?>) storage).update(this);

                    insertedExternally += size - remainder.getCount();
                }

                size = remainder.getCount();
            }
        }

        if (action == Action.PERFORM && inserted - insertedExternally > 0) {
            fluidStorage.add(stack, inserted - insertedExternally, false);
        }

        return remainder;
    }

    @Override
    public synchronized StackListResult<FluidStack> extractFluid(@Nonnull FluidStack stack, long size, int flags, Action action,
                                                                 Predicate<IStorage<FluidStack>> filter) {
        long received = 0;
        long extractedExternally = 0;

        StackListResult<FluidStack> newStack = null;

        for (IStorage<FluidStack> storage : this.fluidStorage.getStorages()) {
            StackListResult<FluidStack> took = null;

            if (filter.test(storage) && storage.getAccessType() != AccessType.INSERT) {
                took = storage.extract(stack, size - received, flags, action);
            }

            if (took != null) {
                // The external storage is responsible for sending changes, we don't need to anymore
                if (storage instanceof IStorageExternal && action == Action.PERFORM) {
                    ((IStorageExternal<?>) storage).update(this);

                    extractedExternally += took.getCount();
                }

                if (newStack == null) {
                    newStack = took;
                } else {
                    newStack.grow(took.getCount());
                }

                received += took.getCount();
            }

            if (size == received) {
                break;
            }
        }

        if (newStack != null && newStack.getCount() - extractedExternally > 0 && action == Action.PERFORM) {
            fluidStorage.remove(newStack.getStack(), newStack.getCount() - extractedExternally, false);
        }

        return newStack;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        if (tag.hasKey(NBT_ENERGY)) {
            this.energy.setStored(tag.getInteger(NBT_ENERGY));
        }

        redstoneMode = RedstoneMode.read(tag);

        craftingManager.readFromNbt(tag);

        readerWriterManager.readFromNbt(tag);

        if (tag.hasKey(NBT_ITEM_STORAGE_TRACKER)) {
            itemStorageTracker.readFromNbt(tag.getTagList(NBT_ITEM_STORAGE_TRACKER, Constants.NBT.TAG_COMPOUND));
        }

        if (tag.hasKey(NBT_FLUID_STORAGE_TRACKER)) {
            fluidStorageTracker.readFromNbt(tag.getTagList(NBT_FLUID_STORAGE_TRACKER, Constants.NBT.TAG_COMPOUND));
        }
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        tag.setInteger(NBT_ENERGY, this.energy.getStored());

        redstoneMode.write(tag);

        craftingManager.writeToNbt(tag);

        readerWriterManager.writeToNbt(tag);

        tag.setTag(NBT_ITEM_STORAGE_TRACKER, itemStorageTracker.serializeNbt());
        tag.setTag(NBT_FLUID_STORAGE_TRACKER, fluidStorageTracker.serializeNbt());

        return tag;
    }

    @Override
    public NBTTagCompound writeUpdate(NBTTagCompound tag) {
        super.writeUpdate(tag);

        tag.setInteger(NBT_ENERGY_TYPE, getEnergyType().getId());

        return tag;
    }

    @Override
    public void readUpdate(NBTTagCompound tag) {
        if (tag.hasKey(NBT_ENERGY_TYPE)) {
            this.energyType = ControllerEnergyType.getById(tag.getInteger(NBT_ENERGY_TYPE));
        }

        super.readUpdate(tag);
    }

    @Override
    public void visit(Operator operator) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos pos = this.pos.offset(facing);

            TileEntity tile = world.getTileEntity(pos);

            // Little hack to support not conducting through covers (if the cover is right next to the controller).
            if (tile != null && tile.hasCapability(NETWORK_NODE_PROXY_CAPABILITY, facing.getOpposite())) {
                INetworkNodeProxy otherNodeProxy = NETWORK_NODE_PROXY_CAPABILITY
                        .cast(tile.getCapability(NETWORK_NODE_PROXY_CAPABILITY, facing.getOpposite()));
                INetworkNode otherNode = otherNodeProxy.getNode();

                if (otherNode instanceof ICoverable &&
                        ((ICoverable) otherNode).getCoverManager().hasCover(facing.getOpposite())) {
                    continue;
                }
            }

            operator.apply(world, pos, facing.getOpposite());
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();

        if (world != null && !world.isRemote) {
            for (ICraftingTask task : this.craftingManager.getTasks()) {
                task.onCancelled();
            }

            nodeGraph.disconnectAll();
        }
    }

    @Override
    public void onConnected(INetwork network) {
        Preconditions.checkArgument(this == network, "Should not be connected to another controller");
    }

    @Override
    public void onDisconnected(INetwork network) {
        Preconditions.checkArgument(this == network, "Should not be connected to another controller");
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        this.redstoneMode = mode;

        markNetworkNodeDirty();
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY
                || capability == CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY
                || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(energyProxy);
        }

        if (capability == CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY) {
            return CapabilityNetworkNodeProxy.NETWORK_NODE_PROXY_CAPABILITY.cast(this);
        }

        return super.getCapability(capability, facing);
    }

    @Nonnull
    @Override
    public World world() {
        return world;
    }

    @Nonnull
    @Override
    public World getNetworkNodeWorld() {
        return this.world;
    }

    @Override
    public BlockPos getNetworkNodePos() {
        return this.pos;
    }

    public static int getEnergyScaled(int stored, int capacity, int scale) {
        return (int) ((float) stored / (float) capacity * (float) scale);
    }

    public static ControllerEnergyType getEnergyType(int stored, int capacity) {
        int energy = getEnergyScaled(stored, capacity, 100);

        if (energy <= 0) {
            return ControllerEnergyType.OFF;
        } else if (energy <= 10) {
            return ControllerEnergyType.NEARLY_OFF;
        } else if (energy <= 20) {
            return ControllerEnergyType.NEARLY_ON;
        }

        return ControllerEnergyType.ON;
    }

    public ControllerEnergyType getEnergyType() {
        if (world.isRemote) {
            return energyType;
        }

        if (!redstoneMode.isEnabled(world, pos)) {
            return ControllerEnergyType.OFF;
        }

        return getEnergyType(this.energy.getStored(), this.energy.getCapacity());
    }

    @Override
    public IEnergy getEnergy() {
        return this.energy;
    }

    private void updateEnergyUsage() {
        if (!this.redstoneModeEnabled || this.type == ControllerType.CREATIVE) {
            this.lastEnergyUsage = 0;
            return;
        }

        int usage = RS.INSTANCE.config.controllerBaseUsage;

        for (INetworkNode node : nodeGraph.all()) {
            if (node.isEnabled()) {
                usage += node.getEnergyUsage();
            }
        }

        this.lastEnergyUsage = usage;
    }

    private void updateRedstoneMode() {
        this.redstoneModeEnabled = this.redstoneMode.isEnabled(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return this.lastEnergyUsage;
    }

    @Override
    public INetworkNodeGraph getNodeGraph() {
        return nodeGraph;
    }

    @Override
    public IStorageCache<FluidStack> getFluidStorageCache() {
        return fluidStorage;
    }

    @Override
    public IStorageCache<ItemStack> getItemStorageCache() {
        return itemStorage;
    }

    @Override
    public IReaderWriterManager getReaderWriterManager() {
        return readerWriterManager;
    }

    @Override
    public ICraftingManager getCraftingManager() {
        return craftingManager;
    }

    @Override
    public ISecurityManager getSecurityManager() {
        return securityManager;
    }

    @Override
    public INetworkItemHandler getNetworkItemHandler() {
        return networkItemHandler;
    }

    @Override
    public IItemGridHandler getItemGridHandler() {
        return itemGridHandler;
    }

    @Override
    public IFluidGridHandler getFluidGridHandler() {
        return fluidGridHandler;
    }

    @Override
    public IStorageTracker<ItemStack> getItemStorageTracker() {
        return itemStorageTracker;
    }

    @Override
    public IStorageTracker<FluidStack> getFluidStorageTracker() {
        return fluidStorageTracker;
    }

    @Override
    @Nonnull
    public TileController getNode() {
        return this;
    }

    @Override
    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    @Override
    public BlockPos getPosition() {
        return pos;
    }

    @Nonnull
    @Override
    public ItemStack getItemStack() {
        IBlockState state = world.getBlockState(pos);

        Item item = Item.getItemFromBlock(state.getBlock());

        return new ItemStack(item, 1, state.getBlock().getMetaFromState(state));
    }

    @Override
    public INetwork getNetwork() {
        return this;
    }

    @Override
    public String getId() {
        return null;
    }

    public ControllerType getType() {
        if (type == null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == RSBlocks.CONTROLLER) {
                this.type = state.getValue(BlockController.TYPE);
            }
        }

        return type == null ? ControllerType.NORMAL : type;
    }

    // Cannot use API#getNetworkNodeHashCode or API#isNetworkNodeEqual: it will crash with a AbstractMethodError (getPos).
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TileController)) {
            return false;
        }

        if (this == o) {
            return true;
        }

        TileController otherController = (TileController) o;

        if (world.provider.getDimension() != otherController.world.provider.getDimension()) {
            return false;
        }

        return pos.equals(otherController.pos);
    }

    @Override
    public int hashCode() {
        int result = pos.hashCode();
        result = 31 * result + world.provider.getDimension();

        return result;
    }
}
