package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.StackListEntry;
import com.raoulvdberge.refinedstorage.api.util.StackListResult;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.tile.TileExporter;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.FilterType;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.IUpgradeContainer;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig.Builder;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

public class NetworkNodeExporter extends NetworkNode implements IRSFilterConfigProvider, ICoverable, IUpgradeContainer {
    public static final String ID = "exporter";
    private static final String NBT_COVERS = "Covers";
    private boolean reading = true;
    private final FilterConfig config = (new Builder(this)).allowedFilterModeWhitelist().allowedFilterTypeItemsAndFluids().filterTypeItems().filterSizeNine().compareDamageAndNbt().customFilterTypeSupplier((ft) -> {
        return this.world.isRemote ? FilterType.values()[TileExporter.TYPE.getValue()] : ft;
    }).build();
    private final ItemHandlerUpgrade upgrades = new ItemHandlerUpgrade(4, (slot) -> {
        if (!this.reading && !this.getUpgradeHandler().hasUpgrade(10)) {
            int i;
            for(i = 0; i < this.config.getItemHandler().getSlots(); ++i) {
                ItemStack filteredItem = this.config.getItemHandler().getStackInSlot(i);
                if (filteredItem.getCount() > 1) {
                    filteredItem.setCount(1);
                }
            }

            for(i = 0; i < this.config.getItemHandler().getSlots(); ++i) {
                FluidStack filteredFluid = this.config.getFluidHandler().getFluid(i);
                if (filteredFluid != null && filteredFluid.amount > 0 && filteredFluid.amount != 1000) {
                    filteredFluid.amount = 1000;
                }
            }
        }

        this.markNetworkNodeDirty();
    }, 2, 3, 4, 10);
    private final CoverManager coverManager = new CoverManager(this);
    private int filterSlot;

    public NetworkNodeExporter(World world, BlockPos pos) {
        super(world, pos);
    }

    public int getEnergyUsage() {
        return RS.INSTANCE.config.exporterUsage + this.upgrades.getEnergyUsage();
    }

    public void updateNetworkNode() {
        super.updateNetworkNode();
        if (this.network != null && this.canUpdate() && this.ticks % this.upgrades.getSpeed() == 0) {
            if (this.config.isFilterTypeItem() && !this.config.getItemFilters().isEmpty()) {
                this.updateItemMode();
            } else if (this.config.isFilterTypeFluid() && !this.config.getFluidFilters().isEmpty()) {
                this.updateFluidMode();
            }
        }

    }

    private void updateItemMode() {
        IItemHandler handler = WorldUtils.getItemHandler(this.getFacingTile(), this.getDirection().getOpposite());
        if (handler != null) {
            if (this.filterSlot >= this.getConfig().getItemFilters().size()) {
                this.filterSlot = 0;
            }

            ItemStack slot = this.config.getItemFilters().get(this.filterSlot);
            if (!slot.isEmpty()) {
                int stackSize = this.upgrades.getItemInteractCount();
                if (this.upgrades.hasUpgrade(10)) {
                    stackSize = this.getStackInteractCountForRegulator(handler, slot, stackSize);
                }

                if (stackSize > 0) {
                    int finalStackSize = stackSize;
                    FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                        ItemStack took = this.network.extractItem(slot, Math.min(slot.getMaxStackSize(), finalStackSize), this.config.getCompare(), Action.SIMULATE);
                        if (took.isEmpty()) {
                            if (this.upgrades.hasUpgrade(3)) {
                                this.network.getCraftingManager().request(new SlottedCraftingRequest(this, this.filterSlot), slot, finalStackSize);
                            }
                        } else {
                            ItemStack remainder = ItemHandlerHelper.insertItem(handler, took, true);
                            int correctedStackSize = took.getCount() - remainder.getCount();
                            if (correctedStackSize > 0) {
                                took = this.network.extractItem(slot, correctedStackSize, this.config.getCompare(), Action.PERFORM);
                                ItemHandlerHelper.insertItem(handler, took, false);
                            }
                        }
                    });
                }
            }

            ++this.filterSlot;
        }

    }

    private int getStackInteractCountForRegulator(IItemHandler handler, ItemStack slot, int stackSize) {
        int found = 0;

        int needed;
        for(needed = 0; needed < handler.getSlots(); ++needed) {
            ItemStack stackInConnectedHandler = handler.getStackInSlot(needed);
            if (API.instance().getComparer().isEqual(slot, stackInConnectedHandler, this.config.getCompare())) {
                found += stackInConnectedHandler.getCount();
            }
        }

        needed = 0;

        for(int i = 0; i < this.config.getItemFilters().size(); ++i) {
            if (API.instance().getComparer().isEqualNoQuantity(slot, this.config.getItemFilters().get(i))) {
                needed += this.config.getItemFilters().get(i).getCount();
            }
        }

        return Math.min(stackSize, needed - found);
    }

    private void updateFluidMode() {
        List<FluidStack> fluids = this.config.getFluidFilters();
        if (this.filterSlot >= this.getConfig().getFluidFilters().size()) {
            this.filterSlot = 0;
        }

        IFluidHandler handler = WorldUtils.getFluidHandler(this.getFacingTile(), this.getDirection().getOpposite());
        if (handler != null) {
            FluidStack stack = fluids.get(this.filterSlot);
            long toExtract = 1000L * (long)this.upgrades.getItemInteractCount();
            if (this.upgrades.hasUpgrade(10)) {
                int found = 0;

                int needed;
                for(needed = 0; needed < handler.getTankProperties().length; ++needed) {
                    FluidStack stackInConnectedHandler = handler.getTankProperties()[needed].getContents();
                    if (API.instance().getComparer().isEqual(stack, stackInConnectedHandler, this.config.getCompare())) {
                        found += stackInConnectedHandler.amount;
                    }
                }

                needed = 0;

                for (FluidStack fluid : fluids) {
                    if (API.instance().getComparer().isEqual(stack, fluid, 2)) {
                        needed += fluid.amount;
                    }
                }

                toExtract = Math.min(toExtract, needed - found);
            }

            StackListEntry<FluidStack> stackInStorage = this.network.getFluidStorageCache().getList().getEntry(stack, this.config.getCompare());
            if (stackInStorage != null) {
                toExtract = Math.min(toExtract, stackInStorage.getCount());
                StackListResult<FluidStack> took = this.network.extractFluid(stack, toExtract, this.config.getCompare(), Action.SIMULATE);
                if (took != null) {
                    int filled = handler.fill(took.getFixedStack(), false);
                    if (filled > 0) {
                        took = this.network.extractFluid(stack, (long)filled, this.config.getCompare(), Action.PERFORM);
                        if (took != null) {
                            handler.fill(took.getFixedStack(), true);
                        }
                    }
                }
            } else if (this.upgrades.hasUpgrade(3)) {
                this.network.getCraftingManager().request(this, stack, toExtract);
            }

            ++this.filterSlot;
        }
    }

    public String getId() {
        return "exporter";
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);
        StackUtils.writeItems(this.upgrades, 1, tag);
        tag.setTag("Covers", this.coverManager.writeToNbt());
        return tag;
    }

    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);
        return tag;
    }

    public void read(NBTTagCompound tag) {
        super.read(tag);
        StackUtils.readItems(this.upgrades, 1, tag);
        if (tag.hasKey("Covers")) {
            this.coverManager.readFromNbt(tag.getTagList("Covers", 10));
        }

        this.reading = false;
    }

    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);
    }

    public IItemHandler getDrops() {
        return new CombinedInvWrapper(this.upgrades, this.coverManager.getAsInventory());
    }

    public boolean canConduct(@Nullable EnumFacing direction) {
        return this.coverManager.canConduct(direction);
    }

    public CoverManager getCoverManager() {
        return this.coverManager;
    }

    public ItemHandlerUpgrade getUpgradeHandler() {
        return this.upgrades;
    }

    @Nonnull
    public FilterConfig getConfig() {
        return this.config;
    }
}
