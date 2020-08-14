package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.inventory.fluid.FluidInventory;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerBase;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import com.raoulvdberge.refinedstorage.tile.TileExporter;
import com.raoulvdberge.refinedstorage.tile.config.IComparable;
import com.raoulvdberge.refinedstorage.tile.config.IType;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;

public class NetworkNodeExporter extends NetworkNode implements IComparable, IType, ICoverable {
    public static final String ID = "exporter";

    private static final String NBT_COMPARE = "Compare";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_COVERS = "Covers";
    private static final String NBT_FLUID_FILTERS = "FluidFilters";

    private final ItemHandlerBase itemFilters = new ItemHandlerBase(9, new ListenerNetworkNode(this));
    private final FluidInventory fluidFilters = new FluidInventory(9, new ListenerNetworkNode(this));

    private final ItemHandlerUpgrade upgrades =
            new ItemHandlerUpgrade(4, new ListenerNetworkNode(this), ItemUpgrade.TYPE_SPEED, ItemUpgrade.TYPE_CRAFTING,
                    ItemUpgrade.TYPE_STACK, ItemUpgrade.TYPE_REGULATOR);

    private int compare = IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE;
    private int type = IType.ITEMS;

    private final CoverManager coverManager = new CoverManager(this);

    private int filterSlot;

    public NetworkNodeExporter(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.exporterUsage + upgrades.getEnergyUsage();
    }

    @Override
    public void update() {
        super.update();

        if (network != null && canUpdate() && ticks % upgrades.getSpeed() == 0) {
            if (type == IType.ITEMS) {
                updateItemMode();
            } else if (type == IType.FLUIDS) {
                updateFluidMode();
            }
        }
    }

    private void updateItemMode() {
        IItemHandler handler = WorldUtils.getItemHandler(getFacingTile(), getDirection().getOpposite());

        if (handler != null) {
            while (filterSlot + 1 < itemFilters.getSlots() && itemFilters.getStackInSlot(filterSlot).isEmpty()) {
                filterSlot++;
            }

            // We jump out of the loop above if we reach the maximum slot. If the maximum slot is empty,
            // we waste a tick with doing nothing because it's empty. Hence this check. If we are at the last slot
            // and it's empty, go back to slot 0.
            // We also handle if we exceeded the maximum slot in general.
            if ((filterSlot == itemFilters.getSlots() - 1 && itemFilters.getStackInSlot(filterSlot).isEmpty()) ||
                    (filterSlot >= itemFilters.getSlots())) {
                filterSlot = 0;
            }

            ItemStack slot = itemFilters.getStackInSlot(filterSlot);

            if (!slot.isEmpty()) {
                int stackSize = upgrades.getItemInteractCount();

                if (upgrades.hasUpgrade(ItemUpgrade.TYPE_REGULATOR)) {
                    stackSize = getStackInteractCountForRegulator(handler, slot, stackSize);
                }

                if (stackSize > 0) {
                    ItemStack took =
                            network.extractItem(slot, Math.min(slot.getMaxStackSize(), stackSize), compare,
                                    Action.SIMULATE);

                    if (took.isEmpty()) {
                        if (upgrades.hasUpgrade(ItemUpgrade.TYPE_CRAFTING)) {
                            network.getCraftingManager()
                                    .request(new SlottedCraftingRequest(this, filterSlot), slot, stackSize);
                        }
                    } else {
                        ItemStack remainder = ItemHandlerHelper.insertItem(handler, took, true);

                        int correctedStackSize = took.getCount() - remainder.getCount();

                        if (correctedStackSize > 0) {
                            took = network.extractItem(slot, correctedStackSize, compare, Action.PERFORM);

                            ItemHandlerHelper.insertItem(handler, took, false);
                        }
                    }
                }
            }

            filterSlot++;
        }
    }

    private int getStackInteractCountForRegulator(IItemHandler handler, ItemStack slot, int stackSize) {
        int found = 0;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stackInConnectedHandler = handler.getStackInSlot(i);

            if (API.instance().getComparer().isEqual(slot, stackInConnectedHandler, compare)) {
                found += stackInConnectedHandler.getCount();
            }
        }

        int needed = 0;

        for (int i = 0; i < itemFilters.getSlots(); ++i) {
            if (API.instance().getComparer().isEqualNoQuantity(slot, itemFilters.getStackInSlot(i))) {
                needed += itemFilters.getStackInSlot(i).getCount();
            }
        }

        return Math.min(stackSize, needed - found);
    }

    private void updateFluidMode() {
        FluidStack[] fluids = fluidFilters.getFluids();

        while (filterSlot + 1 < fluids.length && fluids[filterSlot] == null) {
            filterSlot++;
        }

        // We jump out of the loop above if we reach the maximum slot. If the maximum slot is empty,
        // we waste a tick with doing nothing because it's empty. Hence this check. If we are at the last slot
        // and it's empty, go back to slot 0.
        // We also handle if we exceeded the maximum slot in general.
        if ((filterSlot == fluids.length - 1 && fluids[filterSlot] == null) || (filterSlot >= fluids.length)) {
            filterSlot = 0;
        }

        IFluidHandler handler = WorldUtils.getFluidHandler(getFacingTile(), getDirection().getOpposite());

        if (handler == null)
            return;

        FluidStack stack = fluids[filterSlot];

        if (stack == null) {
            filterSlot++;
            return;
        }

        long toExtract = Fluid.BUCKET_VOLUME * upgrades.getItemInteractCount();

        if (upgrades.hasUpgrade(ItemUpgrade.TYPE_REGULATOR)) {
            int found = 0;

            for (int i = 0; i < handler.getTankProperties().length; i++) {
                FluidStack stackInConnectedHandler = handler.getTankProperties()[i].getContents();

                if (API.instance().getComparer().isEqual(stack, stackInConnectedHandler, compare)) {
                    found += stackInConnectedHandler.amount;
                }
            }

            int needed = 0;

            for (int i = 0; i < fluidFilters.getSlots(); ++i) {
                FluidStack fluid = fluidFilters.getFluid(i);
                if (API.instance().getComparer().isEqual(stack, fluid, IComparer.COMPARE_NBT)) {
                    needed += fluid.amount;
                }
            }

            toExtract = Math.min(toExtract, needed - found);
        }

        StackListEntry<FluidStack> stackInStorage = network.getFluidStorageCache().getList().getEntry(stack, compare);

        if (stackInStorage != null) {
            toExtract = Math.min(toExtract, stackInStorage.getCount());

            StackListResult<FluidStack> took = network.extractFluid(stack, toExtract, compare, Action.SIMULATE);

            if (took != null) {
                int filled = handler.fill(took.getFixedStack(), false);

                if (filled > 0) {
                    took = network.extractFluid(stack, (long)filled, compare, Action.PERFORM);
                    if(took != null) {
                        handler.fill(took.getFixedStack(), true);
                    }
                }
            }
        } else if (upgrades.hasUpgrade(ItemUpgrade.TYPE_CRAFTING)) {
            network.getCraftingManager().request(this, stack, toExtract);
        }

        filterSlot++;
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        this.compare = compare;

        markDirty();
    }


    @Override
    public String getId() {
        return ID;
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        StackUtils.writeItems(upgrades, 1, tag);

        tag.setTag(NBT_COVERS, coverManager.writeToNbt());

        return tag;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        tag.setInteger(NBT_COMPARE, compare);
        tag.setInteger(NBT_TYPE, type);

        StackUtils.writeItems(itemFilters, 0, tag);

        tag.setTag(NBT_FLUID_FILTERS, fluidFilters.writeToNbt());

        return tag;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        StackUtils.readItems(upgrades, 1, tag);

        if (tag.hasKey(NBT_COVERS)) {
            coverManager.readFromNbt(tag.getTagList(NBT_COVERS, Constants.NBT.TAG_COMPOUND));
        }
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_COMPARE)) {
            compare = tag.getInteger(NBT_COMPARE);
        }

        if (tag.hasKey(NBT_TYPE)) {
            type = tag.getInteger(NBT_TYPE);
        }

        StackUtils.readItems(itemFilters, 0, tag);

        if (tag.hasKey(NBT_FLUID_FILTERS)) {
            fluidFilters.readFromNbt(tag.getCompoundTag(NBT_FLUID_FILTERS));
        }
    }

    public IItemHandler getUpgrades() {
        return upgrades;
    }

    @Override
    public IItemHandler getDrops() {
        return new CombinedInvWrapper(upgrades, coverManager.getAsInventory());
    }

    @Override
    public int getType() {
        return world.isRemote ? TileExporter.TYPE.getValue() : type;
    }

    @Override
    public void setType(int type) {
        this.type = type;

        markDirty();
    }

    @Override
    public IItemHandlerModifiable getItemFilters() {
        return itemFilters;
    }

    @Override
    public FluidInventory getFluidFilters() {
        return fluidFilters;
    }

    @Override
    public boolean canConduct(@Nullable EnumFacing direction) {
        return coverManager.canConduct(direction);
    }

    @Override
    public CoverManager getCoverManager() {
        return coverManager;
    }
}
