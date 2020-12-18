package com.raoulvdberge.refinedstorage.apiimpl.network.node.diskmanipulator;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.disk.IStorageDiskContainerContext;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.diskdrive.NetworkNodeDiskDrive;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerProxy;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.IUpgradeContainer;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NetworkNodeDiskManipulator extends NetworkNode implements IRSFilterConfigProvider, IStorageDiskContainerContext, IUpgradeContainer {
    public static final String ID = "disk_manipulator";

    public static final int IO_MODE_INSERT = 0;
    public static final int IO_MODE_EXTRACT = 1;

    private static final String NBT_IO_MODE = "IOMode";

    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterModeBlackAndWhitelist()
            .filterModeBlacklist()
            .allowedFilterTypeItemsAndFluids()
            .filterTypeItems()
            .filterSizeNine()
            .compareDamageAndNbt().build();
    private int ioMode = IO_MODE_INSERT;

    private final IStorageDisk<ItemStack>[] itemDisks = new IStorageDisk[6];
    private final IStorageDisk<FluidStack>[] fluidDisks = new IStorageDisk[6];

    private final ItemHandlerUpgrade upgrades =
            new ItemHandlerUpgrade(4, new ListenerNetworkNode(this), ItemUpgrade.TYPE_SPEED, ItemUpgrade.TYPE_STACK) {
                @Override
                public int getItemInteractCount() {
                    int count = super.getItemInteractCount();

                    if (config.isFilterTypeFluid()) {
                        count *= Fluid.BUCKET_VOLUME;
                    }

                    return count * 25;
                }
            };

    private final ItemHandlerBase inputDisks =
            new ItemHandlerBase(3, new ListenerNetworkNode(this), NetworkNodeDiskDrive.VALIDATOR_STORAGE_DISK) {
                @Override
                protected void onContentsChanged(int slot) {
                    super.onContentsChanged(slot);

                    if (!world.isRemote) {
                        StackUtils.createStorages(
                                world,
                                getStackInSlot(slot),
                                slot,
                                itemDisks,
                                fluidDisks,
                                s -> new StorageDiskItemManipulatorWrapper(NetworkNodeDiskManipulator.this, s),
                                s -> new StorageDiskFluidManipulatorWrapper(NetworkNodeDiskManipulator.this, s)
                        );

                        WorldUtils.updateBlock(world, pos);
                    }
                }
            };

    private final ItemHandlerBase outputDisks =
            new ItemHandlerBase(3, new ListenerNetworkNode(this), NetworkNodeDiskDrive.VALIDATOR_STORAGE_DISK) {
                @Override
                protected void onContentsChanged(int slot) {
                    super.onContentsChanged(slot);

                    if (!world.isRemote) {
                        StackUtils.createStorages(
                                world,
                                getStackInSlot(slot),
                                3 + slot,
                                itemDisks,
                                fluidDisks,
                                s -> new StorageDiskItemManipulatorWrapper(NetworkNodeDiskManipulator.this, s),
                                s -> new StorageDiskFluidManipulatorWrapper(NetworkNodeDiskManipulator.this, s)
                        );

                        WorldUtils.updateBlock(world, pos);
                    }
                }
            };

    private final ItemHandlerProxy disks = new ItemHandlerProxy(inputDisks, outputDisks);

    public NetworkNodeDiskManipulator(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.diskManipulatorUsage + upgrades.getEnergyUsage();
    }

    @Override
    public boolean hasConnectivityState() {
        return true;
    }

    @Override
    public void updateNetworkNode() {
        super.updateNetworkNode();

        if (!canUpdate() || ticks % upgrades.getSpeed() != 0) {
            return;
        }

        int slot = 0;
        if (this.config.isFilterTypeItem()) {
            while (slot < 3 && (itemDisks[slot] == null || isItemDiskDone(itemDisks[slot], slot))) {
                slot++;
            }

            if (slot == 3) {
                return;
            }

            IStorageDisk<ItemStack> storage = itemDisks[slot];

            if (ioMode == IO_MODE_INSERT) {
                insertItemIntoNetwork(storage);
            } else if (ioMode == IO_MODE_EXTRACT) {
                extractItemFromNetwork(storage, slot);
            }
        } else if (this.config.isFilterTypeFluid()) {
            while (slot < 3 && (fluidDisks[slot] == null || isFluidDiskDone(fluidDisks[slot], slot))) {
                slot++;
            }

            if (slot == 3) {
                return;
            }

            IStorageDisk<FluidStack> storage = fluidDisks[slot];

            if (ioMode == IO_MODE_INSERT) {
                insertFluidIntoNetwork(storage, slot);
            } else if (ioMode == IO_MODE_EXTRACT) {
                extractFluidFromNetwork(storage, slot);
            }
        }
    }

    private void insertItemIntoNetwork(IStorageDisk<ItemStack> storage) {
        Collection<StackListEntry<ItemStack>> entries = new ObjectArrayList<>(storage.getEntries());
        for (StackListEntry<ItemStack> stack : entries) {
            if (!this.config.acceptsItem(stack.getStack()))
                continue;

            StackListResult<ItemStack> extracted =
                    storage.extract(stack.getStack(), upgrades.getItemInteractCount(), this.config.getCompare(), Action.PERFORM);
            if (extracted == null) {
                continue;
            }

            StackListResult<ItemStack> remainder = network.insertItem(extracted.getStack(), extracted.getCount(), Action.PERFORM);
            if (remainder == null) {
                break;
            }

            storage.insert(remainder.getStack(), remainder.getCount(), Action.PERFORM);
        }
    }

    // Iterate through disk stacks, if none can be inserted, return that it is done processing and can be output.
    private boolean isItemDiskDone(IStorageDisk<ItemStack> storage, int slot) {
        if (ioMode == IO_MODE_INSERT && storage.getStored() == 0) {
            moveDriveToOutput(slot);
            return true;
        }

        // In Extract mode, we just need to check if the disk is full or not.
        if (ioMode == IO_MODE_EXTRACT) {
            if (storage.getStored() == storage.getCapacity()) {
                moveDriveToOutput(slot);
                return true;
            } else {
                return false;
            }
        }

        Collection<StackListEntry<ItemStack>> entries = new ObjectArrayList<>(storage.getEntries());
        for (StackListEntry<ItemStack> entry : entries) {
            StackListResult<ItemStack> extracted =
                    storage.extract(entry.getStack(), upgrades.getItemInteractCount(), this.config.getCompare(), Action.SIMULATE);
            if (extracted == null) {
                continue;
            }

            StackListResult<ItemStack> remainder =
                    network.insertItem(extracted.getStack(), extracted.getCount(), Action.SIMULATE);
            if (remainder == null) { //An item could be inserted (no remainders when trying to). This disk isn't done.
                return false;
            }
        }
        return true;
    }

    private void extractItemFromNetwork(IStorageDisk<ItemStack> storage, int slot) {
        StackListResult<ItemStack> extracted = null;
        int i = 0;

        if (this.config.isItemFilterEmpty()) {
            ItemStack toExtract = null;
            List<StackListEntry<ItemStack>> networkItems = new ObjectArrayList<>(network.getItemStorageCache().getList().getStacks());
            int j = 0;

            while ((toExtract == null || toExtract.isEmpty()) && j < networkItems.size()) {
                toExtract = networkItems.get(j++).getStack();
            }

            if (toExtract != null) {
                extracted = network.extractItem(toExtract, (long) upgrades.getItemInteractCount(), this.config.getCompare(), Action.PERFORM);
            }
        } else {
            while (this.config.getItemFilters().getSlots() > i && extracted == null) {
                ItemStack filterStack = ItemStack.EMPTY;

                while (this.config.getItemFilters().getSlots() > i && filterStack.isEmpty()) {
                    filterStack = this.config.getItemFilters().getStackInSlot(i++);
                }

                if (!filterStack.isEmpty()) {
                    extracted = network.extractItem(filterStack, (long) upgrades.getItemInteractCount(), this.config.getCompare(), Action.PERFORM);
                }
            }
        }

        if (extracted == null) {
            moveDriveToOutput(slot);
            return;
        }

        StackListResult<ItemStack> remainder = storage.insert(extracted.getStack(), extracted.getCount(), Action.PERFORM);

        if (remainder != null) {
            network.insertItem(remainder.getStack(), remainder.getCount(), Action.PERFORM);
        }
    }

    private void insertFluidIntoNetwork(IStorageDisk<FluidStack> storage, int slot) {
        if (network == null)
            return;

        List<StackListEntry<FluidStack>> entries = new ArrayList<>(storage.getEntries());

        StackListResult<FluidStack> extracted = null;
        int i = 0;

        while (extracted == null && entries.size() > i) {
            StackListEntry<FluidStack> stack = entries.get(i++);

            extracted = storage.extract(stack.getStack(), upgrades.getItemInteractCount(), this.config.getCompare(), Action.PERFORM);
        }

        if (extracted == null) {
            moveDriveToOutput(slot);
            return;
        }

        StackListResult<FluidStack> remainder = network.insertFluid(extracted.getStack(), extracted.getCount(), Action.PERFORM);

        if (remainder != null) {
            storage.insert(remainder.getStack(), remainder.getCount(), Action.PERFORM);
        }
    }

    private boolean isFluidDiskDone(IStorageDisk<FluidStack> storage, int slot) {
        if (ioMode == IO_MODE_INSERT && storage.getStored() == 0) {
            moveDriveToOutput(slot);
            return true;
        }

        //In Extract mode, we just need to check if the disk is full or not.
        if (ioMode == IO_MODE_EXTRACT) {
            if (storage.getStored() == storage.getCapacity()) {
                moveDriveToOutput(slot);
                return true;
            } else {
                return false;
            }
        }

        Collection<StackListEntry<FluidStack>> entries = new ObjectArrayList<>(storage.getEntries());
        for (StackListEntry<FluidStack> entry : entries) {
            StackListResult<FluidStack> extracted =
                    storage.extract(entry.getStack(), upgrades.getItemInteractCount(), this.config.getCompare(), Action.SIMULATE);
            if (extracted == null) {
                continue;
            }

            StackListResult<FluidStack> remainder = network.insertFluid(extracted.getStack(), extracted.getCount(), Action.SIMULATE);
            if (remainder == null) { // A fluid could be inserted (no remainders when trying to). This disk isn't done.
                return false;
            }
        }
        return true;
    }

    private void extractFluidFromNetwork(IStorageDisk<FluidStack> storage, int slot) {
        if (network == null)
            return;

        FluidStack extracted = null;
        int i = 0;

        if (this.config.isFluidFilterEmpty()) {
            FluidStack toExtract = null;
            List<StackListEntry<FluidStack>> networkFluids = new ObjectArrayList<>(network.getFluidStorageCache().getList().getStacks());

            int j = 0;

            while ((toExtract == null || toExtract.amount == 0) && j < networkFluids.size()) {
                toExtract = networkFluids.get(j++).getStack();
            }

            if (toExtract != null) {
                extracted = network.extractFluid(toExtract, upgrades.getItemInteractCount(), this.config.getCompare(), Action.PERFORM);
            }
        } else {
            while (this.config.getFluidFilters().getSlots() > i && extracted == null) {
                FluidStack filterStack = null;

                while (this.config.getFluidFilters().getSlots() > i && filterStack == null) {
                    filterStack = this.config.getFluidFilters().getFluid(i++);
                }

                if (filterStack != null) {
                    extracted = network.extractFluid(filterStack, upgrades.getItemInteractCount(), this.config.getCompare(), Action.PERFORM);
                }
            }
        }

        if (extracted == null) {
            moveDriveToOutput(slot);
            return;
        }

        StackListResult<FluidStack> remainder = storage.insert(extracted, extracted.amount, Action.PERFORM);

        if (remainder != null) {
            network.insertFluid(remainder.getStack(), remainder.getCount(), Action.PERFORM);
        }
    }

    private void moveDriveToOutput(int slot) {
        ItemStack disk = inputDisks.getStackInSlot(slot);
        if (!disk.isEmpty()) {
            int i = 0;
            while (i < 3 && !outputDisks.getStackInSlot(i).isEmpty()) {
                i++;
            }

            if (i == 3) {
                return;
            }

            inputDisks.extractItem(slot, 1, false);
            outputDisks.insertItem(i, disk, false);
        }
    }

    public int getIoMode() {
        return ioMode;
    }

    public void setIoMode(int ioMode) {
        this.ioMode = ioMode;
    }

    public IItemHandler getInputDisks() {
        return inputDisks;
    }

    public IItemHandler getOutputDisks() {
        return outputDisks;
    }

    public ItemHandlerProxy getDisks() {
        return disks;
    }

    public IStorageDisk<ItemStack>[] getItemDisks() {
        return itemDisks;
    }

    public IStorageDisk<FluidStack>[] getFluidDisks() {
        return fluidDisks;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        StackUtils.readItems(upgrades, 3, tag);
        StackUtils.readItems(inputDisks, 4, tag);
        StackUtils.readItems(outputDisks, 5, tag);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        StackUtils.writeItems(upgrades, 3, tag);
        StackUtils.writeItems(inputDisks, 4, tag);
        StackUtils.writeItems(outputDisks, 5, tag);

        return tag;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);
        tag.setInteger(NBT_IO_MODE, ioMode);

        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_IO_MODE)) {
            ioMode = tag.getInteger(NBT_IO_MODE);
        }
    }

    @Override
    public IItemHandler getDrops() {
        return new CombinedInvWrapper(inputDisks, outputDisks, upgrades);
    }

    @Override
    public AccessType getAccessType() {
        return AccessType.INSERT_EXTRACT;
    }

    @Override
    public ItemHandlerUpgrade getUpgradeHandler() {
        return this.upgrades;
    }

    @Nonnull
    @Override
    public FilterConfig getConfig() {
        return this.config;
    }
}
