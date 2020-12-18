package com.raoulvdberge.refinedstorage.apiimpl.network.node;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.raoulvdberge.refinedstorage.inventory.item.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.inventory.listener.ListenerNetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import com.raoulvdberge.refinedstorage.tile.TileDiskDrive;
import com.raoulvdberge.refinedstorage.tile.TileImporter;
import com.raoulvdberge.refinedstorage.tile.config.FilterConfig;
import com.raoulvdberge.refinedstorage.tile.config.FilterType;
import com.raoulvdberge.refinedstorage.tile.config.IRSFilterConfigProvider;
import com.raoulvdberge.refinedstorage.tile.config.IUpgradeContainer;
import com.raoulvdberge.refinedstorage.util.StackUtils;
import com.raoulvdberge.refinedstorage.util.WorldUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NetworkNodeImporter extends NetworkNode implements IRSFilterConfigProvider, ICoverable, IUpgradeContainer {
    public static final String ID = "importer";

    private static final String NBT_COVERS = "Covers";

    private final ItemHandlerUpgrade upgrades = new ItemHandlerUpgrade(4, new ListenerNetworkNode(this), ItemUpgrade.TYPE_SPEED, ItemUpgrade.TYPE_STACK);
    private final FilterConfig config = new FilterConfig.Builder(this)
            .allowedFilterModeBlackAndWhitelist()
            .filterModeBlacklist()
            .allowedFilterTypeItemsAndFluids()
            .filterTypeItems()
            .filterSizeNine()
            .compareDamageAndNbt()
            .customFilterTypeSupplier((ft) -> world.isRemote ? FilterType.values()[TileImporter.TYPE.getValue()] : ft).build();

    private final CoverManager coverManager = new CoverManager(this);

    private int currentSlot;

    public NetworkNodeImporter(World world, BlockPos pos) {
        super(world, pos);
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.importerUsage + upgrades.getEnergyUsage();
    }

    @Override
    public void updateNetworkNode() {
        super.updateNetworkNode();

        if (network == null || !canUpdate()) {
            return;
        }

        if (this.config.isFilterTypeItem()) {
            TileEntity facing = getFacingTile();
            IItemHandler handler = WorldUtils.getItemHandler(facing, getDirection().getOpposite());

            if (facing instanceof TileDiskDrive || handler == null) {
                return;
            }

            int handlerSlotCount = handler.getSlots();
            if (currentSlot >= handlerSlotCount) {
                currentSlot = 0;
            }

            if (handlerSlotCount > 0) {
                ItemStack stack = handler.getStackInSlot(currentSlot);
                while (currentSlot + 1 < handlerSlotCount && stack.isEmpty()) {
                    currentSlot++;
                    stack = handler.getStackInSlot(currentSlot);
                }

                if (!this.config.acceptsItem(stack)) {
                    currentSlot++;
                } else if (ticks % upgrades.getSpeed() == 0) {
                    ItemStack result = handler.extractItem(currentSlot, upgrades.getItemInteractCount(), true);

                    if (!result.isEmpty() && network.insertItem(result, result.getCount(), Action.SIMULATE) == null) {
                        result = handler.extractItem(currentSlot, upgrades.getItemInteractCount(), false);

                        if (!result.isEmpty()) {
                            network.insertItemTracked(result, result.getCount());
                        }
                    } else {
                        currentSlot++;
                    }
                }
            }
        } else if (this.config.isFilterTypeFluid() && ticks % upgrades.getSpeed() == 0) {
            IFluidHandler handler = WorldUtils.getFluidHandler(getFacingTile(), getDirection().getOpposite());

            if (handler != null) {
                FluidStack stack = handler.drain(Fluid.BUCKET_VOLUME, false);

                if (stack != null && this.config.acceptsFluid(stack) && network.insertFluid(stack, (long)stack.amount, Action.SIMULATE) == null) {
                    FluidStack toDrain = handler.drain(Fluid.BUCKET_VOLUME * upgrades.getItemInteractCount(), false);

                    if (toDrain != null) {
                        FluidStack remainder = network.insertFluidTracked(toDrain, toDrain.amount);
                        if (remainder != null) {
                            toDrain.amount -= remainder.amount;
                        }

                        handler.drain(toDrain, true);
                    }
                }
            }
        }
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
        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);
    }

    @Override
    public IItemHandler getDrops() {
        return new CombinedInvWrapper(upgrades, coverManager.getAsInventory());
    }

    @Override
    public boolean canConduct(@Nullable EnumFacing direction) {
        return coverManager.canConduct(direction);
    }

    @Override
    public CoverManager getCoverManager() {
        return coverManager;
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
